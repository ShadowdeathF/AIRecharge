package com.orionkey.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.common.PageResult;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.orionkey.finance.FinanceSupport.*;

/** Immutable settlement documents plus append-only differences, reserves and actual cash registrations. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinanceSettlementService {
    private final FinanceTradeRepository trades;
    private final FinancePersonRepository people;
    private final FinanceSettlementRepository settlements;
    private final FinancePostingRepository postings;
    private final FinanceAdjustmentRepository adjustments;
    private final FinanceCashMovementRepository cash;
    private final FinanceSupport support;
    private final FinanceTime time;
    private final ObjectMapper mapper;

    public record ProfitInput(List<UUID> tradeIds,String idempotencyKey) {}
    public record QuarterInput(String quarter,String idempotencyKey) {}
    public record AdjustInput(UUID tradeId,BigDecimal incomeDelta,BigDecimal costDelta,UUID referrerId,boolean changeReferrer,String reason,String idempotencyKey) {}
    public record PersonInput(String name,String baseRole,boolean enabled,BigDecimal dividendWeight,Long version) {}
    public record CashInput(String kind,UUID personId,UUID settlementId,BigDecimal amount,LocalDate happenedOn,String reference,String reason,String idempotencyKey,UUID reversesId) {}
    public record Basis(UUID tradeId,BigDecimal profit,UUID referrerId) {}
    public record ProfitSnapshot(Map<String,UUID> roles,Map<UUID,String> names,List<Basis> basis) {}
    public record QuarterSnapshot(String quarter,List<UUID> profitGroups,List<ProfitMath.Weight> weights,Map<UUID,String> names,BigDecimal originalAmount) {}
    public record Document(FinanceSettlement document,List<FinancePosting> postings,List<FinanceSettlement> adjustments) {}
    public record ProfitPreview(List<FinanceTrade> trades,ProfitMath.Distribution distribution,Map<String,UUID> roles,Map<UUID,String> names) {}
    public record QuarterPreview(String quarter,BigDecimal amount,Map<UUID,BigDecimal> people,List<ProfitMath.Weight> weights,Map<UUID,String> names,List<UUID> profitGroups,boolean closed,boolean alreadyConfirmed,boolean canConfirm) {}

    public List<FinancePerson> people(){return people.findAll(Sort.by("createdAt").ascending().and(Sort.by("id")));}
    public PageResult<FinanceSettlement> documents(int page,int size){
        if(page<1 || size<1 || size>100)throw bad("分页参数无效");var result=settlements.findAll(PageRequest.of(page-1,size,Sort.by("createdAt").descending().and(Sort.by("id"))));return PageResult.of(result,result.getContent());
    }
    public Document detail(UUID id){
        return new Document(settlements.findById(id).orElseThrow(()->bad("结算单不存在")),postings.findBySettlementIdOrderByCreatedAtAsc(id),settlements.findByOriginIdOrderByCreatedAtAsc(id));
    }
    public List<FinanceAdjustment> tradeAdjustments(UUID id){return adjustments.findByTradeIdOrderBySequenceNumberAsc(id);}
    @Transactional public FinancePerson savePerson(UUID id,PersonInput input,UUID actor){
        support.guard();String name=text(input.name(),80,"人员姓名");
        String role=input.baseRole()==null?"OTHER":input.baseRole();if(!List.of("TECH","BOSS","FINANCE","OTHER").contains(role))throw bad("分成职位无效");
        BigDecimal weight=input.dividendWeight();if(weight==null || weight.signum()<0 || weight.scale()>6 || weight.compareTo(new BigDecimal("1000000"))>0)throw bad("分红权重须为0到1000000之间、最多6位小数");
        FinancePerson person=id==null?new FinancePerson():people.findById(id).orElseThrow(()->bad("人员不存在"));
        if(id!=null)version(person.getVersion(),input.version());
        if(input.enabled() && !role.equals("OTHER") && people.findByEnabledTrueOrderByCreatedAtAsc().stream().anyMatch(p->!p.getId().equals(id) && role.equals(p.getBaseRole())))throw bad("此基础分成职位已有人员，请先调整原职位");
        String before=id==null?null:support.json(person);person.setName(name);person.setBaseRole(role);person.setEnabled(input.enabled());person.setDividendWeight(weight);people.save(person);
        support.audit("finance.person",person.getId(),before,input,null,actor);return person;
    }
    public List<FinanceTrade> candidates(){return trades.findByStatusOrderByConfirmedAtAsc("CONFIRMED");}
    public ProfitPreview previewProfit(ProfitInput input){
        if(input.tradeIds()==null || input.tradeIds().isEmpty() || input.tradeIds().size()>1000 || new HashSet<>(input.tradeIds()).size()!=input.tradeIds().size())throw bad("请选择1到1000笔不重复的已确认交易");
        List<FinanceTrade> selected=new ArrayList<>();List<ProfitMath.ProfitLine> basis=new ArrayList<>();
        for(UUID id:input.tradeIds()){
            FinanceTrade trade=trades.findById(id).orElseThrow(()->bad("交易不存在"));
            if(!"CONFIRMED".equals(trade.getStatus()) || !trade.isReceiptFinal() || trade.getCostAmount()==null)throw bad("所选交易有未确认收入、成本或已结算记录，请刷新");
            selected.add(trade);basis.add(new ProfitMath.ProfitLine(trade.getReceivedAmount().subtract(trade.getCostAmount()),trade.getReferrerId()));
        }
        Map<String,UUID> roles=roles();var distribution=ProfitMath.calculate(basis,roles);
        return new ProfitPreview(selected,distribution,roles,names(distribution.people().keySet()));
    }
    @Transactional public Document confirmProfit(ProfitInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"结算幂等标识");var prior=settlements.findByIdempotencyKey(key);
        if(prior.isPresent()){
            if(!"PROFIT".equals(prior.get().getKind()) || !new HashSet<>(read(prior.get().getSnapshotJson(),ProfitSnapshot.class).basis().stream().map(Basis::tradeId).toList()).equals(new HashSet<>(input.tradeIds())))throw bad("结算幂等标识与原请求不同");
            return detail(prior.get().getId());
        }
        var preview=previewProfit(input);var distribution=preview.distribution();
        var basis=preview.trades().stream().map(t->new Basis(t.getId(),t.getReceivedAmount().subtract(t.getCostAmount()),t.getReferrerId())).toList();
        FinanceSettlement document=newDocument("PROFIT",null,key,actor);document.setSnapshotJson(support.json(new ProfitSnapshot(preview.roles(),preview.names(),basis)));
        document.setTotalAmount(distribution.profit());document.setOperatingBase(distribution.operatingBase());document.setRoundingAdjustment(distribution.roundingAdjustment());document.setOperatingAmount(distribution.operating());document.setDividendAmount(distribution.dividend());settlements.save(document);
        postTargets(document,document.getId(),distribution.people(),distribution.operating(),distribution.dividend());
        for(FinanceTrade trade:preview.trades()){trade.setStatus("SETTLED");trade.setSettlementId(document.getId());trades.save(trade);}
        support.audit("profit.settle",document.getId(),null,Map.of("trade_ids",input.tradeIds(),"profit",distribution.profit()),null,actor);return detail(document.getId());
    }

    public QuarterPreview previewQuarter(String quarter){
        LocalDate end=quarterEnd(quarter);boolean closed=time.today().isAfter(end),already=settlements.findByQuarterKey(quarter).isPresent();
        Set<UUID> claimed=new HashSet<>();for(var previous:settlements.findByKindOrderByAccountingDateAsc("QUARTER"))claimed.addAll(read(previous.getSnapshotJson(),QuarterSnapshot.class).profitGroups());
        List<UUID> groups=new ArrayList<>();BigDecimal amount=BigDecimal.ZERO;
        for(var group:settlements.findByKindAndAccountingDateLessThanEqualOrderByAccountingDateAsc("PROFIT",end)){
            if(claimed.contains(group.getId()))continue;
            BigDecimal reserve=accountTotal(postings.findByOriginId(group.getId()),"DIVIDEND_RESERVE",null);
            if(reserve.signum()!=0){groups.add(group.getId());amount=amount.add(reserve);}
        }
        var recipients=people.findByEnabledTrueOrderByCreatedAtAsc().stream().filter(p->p.getDividendWeight().signum()>0)
            .sorted(Comparator.comparingInt((FinancePerson p)->roleOrder(p.getBaseRole())).thenComparing(p->p.getId().toString())).toList();
        var weights=recipients.stream().map(p->new ProfitMath.Weight(p.getId(),p.getDividendWeight())).toList();
        Map<UUID,BigDecimal> allocation=weights.isEmpty()?Map.of():ProfitMath.weighted(amount,weights);
        return new QuarterPreview(quarter,amount,allocation,weights,names(allocation.keySet()),groups,closed,already,closed && !already && amount.signum()>0 && !weights.isEmpty());
    }
    @Transactional public Document confirmQuarter(QuarterInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"分红幂等标识");var previous=settlements.findByIdempotencyKey(key);
        if(previous.isPresent()){
            if(!"QUARTER".equals(previous.get().getKind()) || !input.quarter().equals(previous.get().getQuarterKey()))throw bad("分红幂等标识与原请求不同");
            return detail(previous.get().getId());
        }
        var preview=previewQuarter(input.quarter());
        if(!preview.closed())throw bad("季度尚未结束，可预览但不能提前分红");
        if(preview.alreadyConfirmed())throw bad("此季度已结算，不能重复分红");
        if(!preview.canConfirm())throw bad("没有可分配的无推荐人奖励余额，或未配置分红人员");
        FinanceSettlement document=newDocument("QUARTER",null,key,actor);document.setQuarterKey(input.quarter());document.setTotalAmount(preview.amount());document.setDividendAmount(preview.amount().negate());
        document.setSnapshotJson(support.json(new QuarterSnapshot(input.quarter(),preview.profitGroups(),preview.weights(),preview.names(),preview.amount())));settlements.save(document);
        postTargets(document,document.getId(),preview.people(),BigDecimal.ZERO,preview.amount().negate());
        support.audit("dividend.quarter",document.getId(),null,preview,null,actor);return detail(document.getId());
    }

    @Transactional public Document adjust(AdjustInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"调整幂等标识"),reason=text(input.reason(),500,"调整原因");
        BigDecimal income=amount(input.incomeDelta()==null?BigDecimal.ZERO:input.incomeDelta(),true),cost=amount(input.costDelta()==null?BigDecimal.ZERO:input.costDelta(),true);
        var prior=adjustments.findByIdempotencyKey(key);
        if(prior.isPresent()){
            var old=prior.get();if(!old.getTradeId().equals(input.tradeId()) || old.getIncomeDelta().compareTo(income)!=0 || old.getCostDelta().compareTo(cost)!=0
                || old.isChangeReferrer()!=input.changeReferrer() || !Objects.equals(old.getReferrerId(),input.referrerId()) || !old.getReason().equals(reason))throw bad("调整幂等标识与原请求不同");
            var document=settlements.findByIdempotencyKey("adjust:"+old.getId()).orElseThrow();return detail(document.getId());
        }
        FinanceTrade trade=trades.lockById(input.tradeId()).orElseThrow(()->bad("交易不存在"));
        if(!"SETTLED".equals(trade.getStatus()) || trade.getSettlementId()==null)throw bad("此接口只接受已结算交易，未结算交易请在对账中更正并重新确认");
        if(income.signum()==0 && cost.signum()==0 && !input.changeReferrer())throw bad("没有需要登记的调整");
        if(input.changeReferrer() && input.referrerId()!=null)people.findById(input.referrerId()).orElseThrow(()->bad("推荐人不存在"));
        var previous=adjustments.findByTradeIdOrderBySequenceNumberAsc(trade.getId());
        BigDecimal finalIncome=trade.getReceivedAmount().add(income),finalCost=trade.getCostAmount().add(cost);
        for(var adjustment:previous){finalIncome=finalIncome.add(adjustment.getIncomeDelta());finalCost=finalCost.add(adjustment.getCostDelta());}
        if(finalIncome.signum()<0 || finalCost.signum()<0)throw bad("累计收入或成本不能为负，请核对退款和成本冲回金额");
        var all=adjustments.findByOriginIdOrderBySequenceNumberAsc(trade.getSettlementId());
        FinanceAdjustment row=new FinanceAdjustment();row.setTradeId(trade.getId());row.setOriginId(trade.getSettlementId());row.setSequenceNumber(all.isEmpty()?1:all.getLast().getSequenceNumber()+1);
        row.setIncomeDelta(income);row.setCostDelta(cost);row.setChangeReferrer(input.changeReferrer());row.setReferrerId(input.referrerId());row.setReason(reason);row.setIdempotencyKey(key);row.setCreatedBy(actor);adjustments.save(row);
        var root=settlements.findById(trade.getSettlementId()).orElseThrow();var snapshot=read(root.getSnapshotJson(),ProfitSnapshot.class);
        var entries=new ArrayList<ProfitMath.ProfitLine>();var combined=adjustments.findByOriginIdOrderBySequenceNumberAsc(root.getId());
        for(Basis basis:snapshot.basis()){
            BigDecimal profit=basis.profit();UUID referrer=basis.referrerId();
            for(FinanceAdjustment update:combined)if(update.getTradeId().equals(basis.tradeId())){profit=profit.add(update.getIncomeDelta()).subtract(update.getCostDelta());if(update.isChangeReferrer())referrer=update.getReferrerId();}
            entries.add(new ProfitMath.ProfitLine(profit,referrer));
        }
        var target=ProfitMath.calculate(entries,snapshot.roles());var booked=postings.findByOriginId(root.getId());
        FinanceSettlement document=newDocument("ADJUSTMENT",root.getId(),"adjust:"+row.getId(),actor);
        document.setSnapshotJson(support.json(Map.of("adjustment",row,"cumulative_target",target,"original_names",snapshot.names())));
        document.setTotalAmount(target.profit().subtract(booked.stream().map(FinancePosting::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));
        document.setOperatingAmount(target.operating().subtract(accountTotal(booked,"OPERATING_RESERVE",null)));document.setDividendAmount(target.dividend().subtract(accountTotal(booked,"DIVIDEND_RESERVE",null)));settlements.save(document);
        postTargets(document,root.getId(),target.people(),target.operating(),target.dividend());
        for(var quarter:settlements.findByKindOrderByAccountingDateAsc("QUARTER")){
            var quarterSnapshot=read(quarter.getSnapshotJson(),QuarterSnapshot.class);
            if(quarterSnapshot.profitGroups().contains(root.getId()))recomputeQuarter(quarter,quarterSnapshot,row,actor);
        }
        support.audit("profit.adjust",trade.getId(),null,row,reason,actor);return detail(document.getId());
    }
    private void recomputeQuarter(FinanceSettlement root,QuarterSnapshot snapshot,FinanceAdjustment adjustment,UUID actor){
        BigDecimal available=BigDecimal.ZERO;
        for(UUID group:snapshot.profitGroups())available=available.add(accountTotal(postings.findByOriginId(group),"DIVIDEND_RESERVE",null));
        var target=ProfitMath.weighted(available,snapshot.weights());var old=postings.findByOriginId(root.getId());
        FinanceSettlement document=newDocument("QUARTER_ADJUSTMENT",root.getId(),"quarter-adjust:"+adjustment.getId(),actor);
        document.setTotalAmount(available.add(accountTotal(old,"DIVIDEND_RESERVE",null)));document.setDividendAmount(document.getTotalAmount().negate());
        document.setSnapshotJson(support.json(Map.of("original_quarter",root.getId(),"adjustment_id",adjustment.getId(),"original_weights",snapshot.weights(),"original_names",snapshot.names(),"cumulative_amount",available,"cumulative_people",target)));settlements.save(document);
        postTargets(document,root.getId(),target,BigDecimal.ZERO,available.negate());
        support.audit("dividend.adjust",document.getId(),null,Map.of("original_quarter",root.getId(),"amount",document.getTotalAmount()),adjustment.getReason(),actor);
    }

    /** Append the difference from all previously booked targets of this frozen group. */
    private void postTargets(FinanceSettlement document,UUID origin,Map<UUID,BigDecimal> targetPeople,BigDecimal operating,BigDecimal dividend){
        var old=postings.findByOriginId(origin);Set<UUID> ids=new LinkedHashSet<>(targetPeople.keySet());
        for(var row:old)if("PERSONAL_PAYABLE".equals(row.getAccount()))ids.add(row.getPersonId());
        for(UUID id:ids)post(document,origin,"PERSONAL_PAYABLE",id,targetPeople.getOrDefault(id,BigDecimal.ZERO).subtract(accountTotal(old,"PERSONAL_PAYABLE",id)));
        post(document,origin,"OPERATING_RESERVE",null,operating.subtract(accountTotal(old,"OPERATING_RESERVE",null)));
        post(document,origin,"DIVIDEND_RESERVE",null,dividend.subtract(accountTotal(old,"DIVIDEND_RESERVE",null)));
    }
    private void post(FinanceSettlement document,UUID origin,String account,UUID person,BigDecimal amount){
        if(amount.signum()==0)return;
        FinancePosting posting=new FinancePosting();posting.setSettlementId(document.getId());posting.setOriginId(origin);posting.setAccount(account);posting.setPersonId(person);posting.setAmount(ProfitMath.money(amount));posting.setAccountingDate(document.getAccountingDate());
        posting.setPostingKey(document.getId()+":"+account+":"+(person==null?"-":person));postings.save(posting);
    }
    private BigDecimal accountTotal(List<FinancePosting> entries,String account,UUID person){return entries.stream().filter(p->account.equals(p.getAccount()) && Objects.equals(person,p.getPersonId())).map(FinancePosting::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private FinanceSettlement newDocument(String kind,UUID origin,String key,UUID actor){FinanceSettlement d=new FinanceSettlement();d.setKind(kind);d.setOriginId(origin);d.setIdempotencyKey(key);d.setAccountingDate(time.today());d.setCreatedBy(actor);return d;}
    private Map<String,UUID> roles(){
        Map<String,UUID> result=new LinkedHashMap<>();for(var person:people.findByEnabledTrueOrderByCreatedAtAsc())if(List.of("TECH","BOSS","FINANCE").contains(person.getBaseRole()))result.put(person.getBaseRole(),person.getId());
        if(result.size()!=3)throw bad("请先配置技术总监、总经理、财务总监三名基础分成人员");return result;
    }
    private Map<UUID,String> names(Collection<UUID> ids){Map<UUID,String> names=new LinkedHashMap<>();for(UUID id:ids)names.put(id,people.findById(id).orElseThrow(()->bad("分成人员不存在")).getName());return names;}
    private int roleOrder(String role){return "TECH".equals(role)?0:"BOSS".equals(role)?1:"FINANCE".equals(role)?2:3;}
    private LocalDate quarterEnd(String text){
        if(text==null || !text.matches("20[0-9]{2}-Q[1-4]"))throw bad("季度格式须为2026-Q3");
        int year=Integer.parseInt(text.substring(0,4)),quarter=text.charAt(6)-'0';return LocalDate.of(year,(quarter-1)*3+1,1).plusMonths(3).minusDays(1);
    }
    private <T>T read(String json,Class<T> type){try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("结算快照无法读取，禁止继续入账",e);}}

    public BigDecimal personBalance(UUID id){
        BigDecimal result=postings.personal(id);
        for(var row:cash.findByPersonIdOrderByCreatedAtAsc(id)){if("PAYOUT".equals(row.getKind()))result=result.subtract(row.getAmount());if("RECOVERY".equals(row.getKind()))result=result.add(row.getAmount());}
        return result;
    }
    public BigDecimal operatingBalance(){
        BigDecimal result=postings.total("OPERATING_RESERVE");for(var row:cash.findByKindOrderByHappenedOnDesc("EXPENSE"))result=result.subtract(row.getAmount());for(var row:cash.findByKindOrderByHappenedOnDesc("EXPENSE_REVERSAL"))result=result.add(row.getAmount());return result;
    }
    public BigDecimal dividendBalance(){return postings.total("DIVIDEND_RESERVE");}
    public Map<String,Object> overview(){
        List<Map<String,Object>> accounts=new ArrayList<>();for(var person:people()){Map<String,Object> row=new LinkedHashMap<>();row.put("person",person);row.put("balance",personBalance(person.getId()));accounts.add(row);}
        return Map.of("operating_balance",operatingBalance(),"dividend_balance",dividendBalance(),"people",accounts,"candidate_count",candidates().size(),"expenses",cash.findByKindOrderByHappenedOnDesc("EXPENSE"),"expense_reversals",cash.findByKindOrderByHappenedOnDesc("EXPENSE_REVERSAL"));
    }
    @Transactional public FinanceCashMovement recordCash(CashInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"记账幂等标识");BigDecimal value=amount(input.amount(),false);
        if(value.signum()<=0)throw bad("实际资金金额必须大于零");date(input.happenedOn());if(input.happenedOn().isAfter(time.today()))throw bad("资金日期不能晚于财务当前日期");
        if(!List.of("PAYOUT","RECOVERY","EXPENSE","EXPENSE_REVERSAL").contains(input.kind()))throw bad("资金记录类型无效");
        var previous=cash.findByIdempotencyKey(key);
        if(previous.isPresent()){if(!sameCash(previous.get(),input,value))throw bad("记账幂等标识与原请求不同");return previous.get();}
        String reference=input.reference()==null?null:input.reference().trim();
        if(input.kind().equals("PAYOUT") || input.kind().equals("RECOVERY")){
            if(input.personId()==null || !people.existsById(input.personId()))throw bad("请选择有效人员");reference=text(reference,128,"实际资金流水号");
            var duplicate=cash.findByKindAndPersonIdAndReference(input.kind(),input.personId(),reference);
            if(duplicate.isPresent()){if(!sameCash(duplicate.get(),input,value))throw bad("此人员的资金流水已经记账且内容不同");return duplicate.get();}
            BigDecimal balance=personBalance(input.personId());
            if(input.kind().equals("PAYOUT") && (balance.signum()<=0 || value.compareTo(balance)>0))throw bad("支付金额超过当前净应付；负余额已计入抵扣");
            if(input.kind().equals("RECOVERY") && (balance.signum()>=0 || value.compareTo(balance.negate())>0))throw bad("退回金额超过当前应收回的分成余额");
        }else{
            if(input.personId()!=null)throw bad("公司支出不能作为人员付款登记");text(input.reason(),500,"公司支出或冲正说明");
            if(input.kind().equals("EXPENSE_REVERSAL")){
                FinanceCashMovement original=input.reversesId()==null?null:cash.findById(input.reversesId()).orElse(null);
                if(original==null || !"EXPENSE".equals(original.getKind()) || value.compareTo(original.getAmount())!=0 || cash.existsByReversesId(original.getId()))throw bad("须关联尚未冲正的原公司支出并全额冲正");
            }else if(input.reversesId()!=null)throw bad("普通支出不能重复使用冲正关联");
        }
        if(input.settlementId()!=null && !settlements.existsById(input.settlementId()))throw bad("关联结算单不存在");
        if(reference!=null && reference.length()>128)throw bad("资金流水号最多128字符");if(input.reason()!=null && input.reason().length()>500)throw bad("说明最多500字符");
        FinanceCashMovement row=new FinanceCashMovement();row.setKind(input.kind());row.setPersonId(input.personId());row.setSettlementId(input.settlementId());row.setAmount(value);row.setHappenedOn(input.happenedOn());row.setReference(reference);row.setReason(input.reason());row.setIdempotencyKey(key);row.setReversesId(input.reversesId());row.setCreatedBy(actor);cash.save(row);
        support.audit("finance.cash",row.getId(),null,input,input.reason(),actor);return row;
    }
    private boolean sameCash(FinanceCashMovement original,CashInput input,BigDecimal value){return original.getKind().equals(input.kind()) && Objects.equals(original.getPersonId(),input.personId()) && Objects.equals(original.getSettlementId(),input.settlementId()) && original.getAmount().compareTo(value)==0 && Objects.equals(original.getHappenedOn(),input.happenedOn()) && Objects.equals(original.getReversesId(),input.reversesId());}
}

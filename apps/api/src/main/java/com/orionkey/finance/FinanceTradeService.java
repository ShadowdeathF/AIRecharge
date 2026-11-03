package com.orionkey.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.*;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import com.orionkey.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.orionkey.finance.FinanceSupport.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinanceTradeService {
    private final FinanceTradeRepository trades;
    private final FinanceCashMovementRepository cash;
    private final FinancePersonRepository people;
    private final FinanceAdjustmentRepository adjustments;
    private final FinanceAuditRepository audits;
    private final CompanyRepository companies;
    private final ProductRepository products;
    private final ProductSpecRepository specs;
    private final CompanyQuoteRepository quotes;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CardKeyRepository keys;
    private final CardImportBatchRepository cardBatches;
    private final StockAllocationRepository allocations;
    private final ProcurementService procurement;
    private final FinanceSupport support;
    private final ObjectMapper mapper;
    private final FinanceTime time;

    public record ItemInput(UUID productId,UUID specId,int quantity,BigDecimal unitPrice,String priceReason) {}
    public record OfflineInput(UUID companyId,String externalReference,LocalDate businessDate,List<ItemInput> items,UUID referrerId,String note,String idempotencyKey,UUID sourceOrderId) {
        public OfflineInput(UUID company,String external,LocalDate day,List<ItemInput> items,UUID referrer,String note,String key){this(company,external,day,items,referrer,note,key,null);}
    }
    public record TradeLine(String key,UUID productId,UUID specId,String productTitle,String specName,int quantity,BigDecimal unitPrice,BigDecimal cost,UUID quoteId) {}
    public record TradeView(FinanceTrade trade,List<TradeLine> items,List<StockAllocation> allocations,List<FinanceCashMovement> cash,List<FinanceAudit> history,List<FinanceAdjustment> adjustments,BigDecimal effectiveReceived,BigDecimal effectiveCost,UUID effectiveReferrerId) {}
    public record ReceiptInput(BigDecimal amount,LocalDate happenedOn,boolean finalReceipt,String reason,String idempotencyKey,Long version) {}
    public record CostInput(String lineKey,List<UUID> lotIds,Long version) {}
    public record ReferrerInput(UUID referrerId,String reason,Long version) {}
    public record FinalIncomeInput(String reason,Long version) {}
    public record ConfirmInput(UUID id,Long version) {}
    public record ReconcileInput(BigDecimal incomeDelta,BigDecimal costDelta,String reason,String idempotencyKey,Long version) {}
    public record CompanyChangeInput(UUID companyId,String reason,Long version) {}

    public List<TradeLine> items(FinanceTrade trade){
        try{return mapper.readValue(trade.getItemsJson(),new TypeReference<List<TradeLine>>(){});}catch(Exception e){throw new IllegalStateException("交易快照损坏，不能继续核算",e);}
    }
    public TradeView detail(UUID id){return view(trades.findById(id).orElseThrow(()->bad("财务交易不存在")));}
    public List<Map<String,Object>> onlineOptions(UUID company){return trades.onlineOptions(company,PageRequest.of(0,100)).stream().map(order->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",order.getId());row.put("email",order.getEmail());row.put("paid_at",order.getPaidAt());row.put("amount",order.getActualAmount());return row;}).toList();}
    private TradeView view(FinanceTrade trade){
        var changes=adjustments.findByTradeIdOrderBySequenceNumberAsc(trade.getId());
        return new TradeView(trade,items(trade),allocations.findByTradeId(trade.getId()),cash.findByTradeIdOrderByCreatedAtAsc(trade.getId()),audits.findByTargetIdOrderByCreatedAtDesc(trade.getId()),changes,effective(trade,changes,false),effective(trade,changes,true),effectiveReferrer(trade,changes));
    }
    private UUID effectiveReferrer(FinanceTrade trade,List<FinanceAdjustment> changes){UUID id=trade.getReferrerId();for(var change:changes.stream().sorted(java.util.Comparator.comparingLong(FinanceAdjustment::getSequenceNumber)).toList())if(change.isChangeReferrer())id=change.getReferrerId();return id;}
    private BigDecimal effective(FinanceTrade trade,List<FinanceAdjustment> changes,boolean cost){
        BigDecimal value=cost?trade.getCostAmount():trade.getReceivedAmount();if(value==null)return null;
        for(var row:changes)value=value.add(cost?row.getCostDelta():row.getIncomeDelta());return value;
    }

    @Transactional public TradeView createOffline(OfflineInput input,UUID actor){
        if(input==null)throw bad("交易内容不能为空");
        if(input.sourceOrderId()!=null)return linkOnline(input,actor);
        support.guard();String fingerprint=fingerprint(input);
        String external=text(input.externalReference(),120,"企业外部单号");String key=text(input.idempotencyKey(),128,"幂等标识");
        String sourceKey="OFFLINE:"+input.companyId()+":"+external.toLowerCase(Locale.ROOT);
        var previous=trades.findBySourceKey(sourceKey).or(()->trades.findByIdempotencyKey(key));
        if(trades.findByCompanyIdAndExternalReferenceIgnoreCase(input.companyId(),external).filter(t->t.getSourceOrderId()!=null).isPresent())throw bad("此单号已关联平台订单，请在原交易核对，不要重复录入收入");
        try{if(orders.existsById(UUID.fromString(external)))throw bad("该单号属于平台订单，请选择关联已有平台订单");}catch(IllegalArgumentException ignored){}
        if(previous.isPresent()){
            if(!fingerprint.equals(previous.get().getCreationFingerprint()))throw bad("外部单号或幂等标识已经用于不同交易，请先查看原记录");
            return view(previous.get());
        }
        var prepared=prepare(input);
        FinanceTrade trade=new FinanceTrade();trade.setSource("COMPANY_OFFLINE");trade.setCompanyId(prepared.company().getId());trade.setCompanyName(prepared.company().getName());
        trade.setCustomerName(prepared.company().getName());trade.setExternalReference(external);trade.setSourceKey(sourceKey);trade.setIdempotencyKey(key);trade.setCreationFingerprint(fingerprint);
        trade.setBusinessDate(input.businessDate());trade.setDueAmount(prepared.due());trade.setItemsJson(support.json(prepared.items()));trade.setReferrerId(input.referrerId());trade.setCreatedBy(actor);trade.setNote(input.note());trades.save(trade);
        support.audit("trade.offline_create",trade.getId(),null,input,null,actor);return view(trade);
    }
    private TradeView linkOnline(OfflineInput input,UUID actor){
        support.guard();validateLinkedInput(input);String external=text(input.externalReference(),120,"企业外部单号");
        var order=orders.findById(input.sourceOrderId()).orElseThrow(()->bad("来源平台订单不存在"));
        if(input.companyId()==null || !input.companyId().equals(order.getCompanyId()))throw bad("平台订单企业与所选企业不一致，请核对原订单归属");
        var conflict=trades.findByCompanyIdAndExternalReferenceIgnoreCase(input.companyId(),external);
        if(conflict.isPresent() && !order.getId().equals(conflict.get().getSourceOrderId()))throw bad("该企业外部单号已关联其他交易");
        var result=syncOrder(order.getId());var trade=result.trade();
        if(trade.getExternalReference()!=null && !external.equalsIgnoreCase(trade.getExternalReference()))throw bad("此平台订单已关联其他企业单号，请查看原交易");
        if(trade.getExternalReference()==null){trade.setExternalReference(external);trades.save(trade);support.audit("trade.link_online",trade.getId(),null,Map.of("source_order_id",order.getId(),"external_reference",external),null,actor);}
        return view(trade);
    }
    private void validateLinkedInput(OfflineInput input){
        if(input.referrerId()!=null || input.note()!=null && !input.note().isBlank() || input.items()!=null && !input.items().isEmpty())throw bad("关联平台订单只补企业外部单号；推荐人、收入与成本请在原交易详情核对，不要在导入行覆盖");
    }
    private record Prepared(Company company,List<TradeLine> items,BigDecimal due) {}
    private Prepared prepare(OfflineInput input){
        if(input==null)throw bad("交易内容不能为空");
        date(input.businessDate());String external=text(input.externalReference(),120,"企业外部单号");
        if(input.companyId()==null)throw bad("请选择企业");
        Company company=companies.findById(input.companyId()).filter(Company::isEnabled).orElseThrow(()->bad("企业不存在或已停用"));
        if(input.items()==null || input.items().isEmpty() || input.items().size()>100)throw bad("交易须包含1到100行商品");
        validatePerson(input.referrerId());if(input.note()!=null && input.note().length()>500)throw bad("备注最多500字符");
        List<TradeLine> result=new ArrayList<>();BigDecimal due=BigDecimal.ZERO;
        for(ItemInput entry:input.items()){
            if(entry==null || entry.productId()==null)throw bad("请选择交易商品");
            Product product=products.findById(entry.productId()).filter(p->p.getIsDeleted()==0).orElseThrow(()->bad("商品不存在"));
            ProductSpec spec=null;
            if(product.isSpecEnabled() && entry.specId()==null)throw bad("多规格商品须选择具体规格");
            if(entry.specId()!=null)spec=specs.findById(entry.specId()).filter(s->s.getProductId().equals(product.getId()) && s.getIsDeleted()==0).orElseThrow(()->bad("规格与商品不匹配"));
            String sku=product.getId()+":"+(entry.specId()==null?"-":entry.specId());
            var quote=quotes.findFirstByCompanyIdAndSkuKeyAndActiveTrue(company.getId(),sku);
            BigDecimal defaultPrice=quote.map(CompanyQuote::getUnitPrice).orElse(spec==null?product.getBasePrice():spec.getPrice());
            BigDecimal price=entry.unitPrice()==null?defaultPrice:amount(entry.unitPrice(),false);
            if(price.compareTo(defaultPrice)!=0)text(entry.priceReason(),500,"特殊成交价说明");
            int qty=quantity(entry.quantity());due=due.add(price.multiply(BigDecimal.valueOf(qty)));amount(due,false);
            result.add(new TradeLine(UUID.randomUUID().toString(),product.getId(),entry.specId(),product.getTitle(),spec==null?null:spec.getName(),qty,price,null,quote.map(CompanyQuote::getId).orElse(null)));
        }
        return new Prepared(company,result,amount(due,false));
    }

    @Transactional public TradeView syncOrder(UUID orderId){
        support.guard();Order order=orders.findById(orderId).orElseThrow(()->bad("来源订单不存在"));
        if(order.getPaidAt()==null || !List.of(OrderStatus.PAID,OrderStatus.DELIVERED).contains(order.getStatus()))throw bad("来源订单尚未付款");
        var existing=trades.findBySourceOrderId(orderId);FinanceTrade trade;
        if(existing.isPresent())trade=existing.get();
        else{
            trade=new FinanceTrade();trade.setSourceOrderId(orderId);trade.setSourceKey("ONLINE:"+orderId);trade.setIdempotencyKey("online:"+orderId);
            trade.setSource(order.getCompanyId()==null?"RETAIL_ONLINE":"COMPANY_ONLINE");trade.setCompanyId(order.getCompanyId());trade.setCompanyName(order.getCompanyName());trade.setCustomerName(order.getEmail());
            trade.setBusinessDate(order.getPaidAt().toLocalDate());boolean knownIncome=order.getActualAmount()!=null;BigDecimal received=amount(knownIncome?order.getActualAmount():BigDecimal.ZERO,false);
            trade.setDueAmount(amount(knownIncome?received:order.getTotalAmount()==null?BigDecimal.ZERO:order.getTotalAmount(),false));trade.setReceivedAmount(received);trade.setReceiptFinal(knownIncome);
            List<TradeLine> snapshot=orderItems.findByOrderId(orderId).stream().map(i->new TradeLine(i.getId().toString(),i.getProductId(),i.getSpecId(),i.getProductTitle(),i.getSpecName(),i.getQuantity(),i.getUnitPrice(),null,i.getCompanyQuoteId())).toList();
            trade.setItemsJson(support.json(snapshot));trades.save(trade);
            if(knownIncome){FinanceCashMovement receipt=new FinanceCashMovement();receipt.setTradeId(trade.getId());receipt.setKind("RECEIPT");receipt.setAmount(received);receipt.setHappenedOn(trade.getBusinessDate());receipt.setIdempotencyKey("online-receipt:"+orderId);receipt.setReference(order.getEpayTradeNo());cash.save(receipt);}
            support.audit("trade.sync_order",trade.getId(),null,Map.of("source_order_id",orderId),null,null);
        }
        if("DRAFT".equals(trade.getStatus()) && order.getStatus()==OrderStatus.DELIVERED)attributeDelivered(trade,orderId);
        return view(trade);
    }
    @Transactional public Map<String,Object> syncPaidOrders(){
        support.guard();var batch=trades.unsynced(PageRequest.of(0,50));
        for(Order order:batch)syncOrder(order.getId());
        Set<UUID> synced=new HashSet<>();batch.forEach(o->synced.add(o.getId()));
        // Each request commits a bounded batch so historical backfill does not monopolize financial writes.
        for(FinanceTrade trade:trades.repairable(PageRequest.of(0,50)))if(!synced.contains(trade.getSourceOrderId()))syncOrder(trade.getSourceOrderId());
        trades.flush();return Map.of("synced",batch.size(),"has_more",!trades.unsynced(PageRequest.of(0,1)).isEmpty());
    }
    private void attributeDelivered(FinanceTrade trade,UUID orderId){
        List<CardKey> delivered=keys.findByOrderId(orderId);List<TradeLine> result=new ArrayList<>();
        for(TradeLine line:items(trade)){
            if(line.cost()!=null){result.add(line);continue;}
            List<CardKey> actual=delivered.stream().filter(k->line.key().equals(String.valueOf(k.getOrderItemId()))).toList();
            Map<UUID,Integer> lotCounts=new LinkedHashMap<>();boolean complete=actual.size()==line.quantity();
            for(CardKey key:actual){
                var batch=key.getImportBatchId()==null?Optional.<CardImportBatch>empty():cardBatches.findById(key.getImportBatchId());
                if(batch.isEmpty() || batch.get().getProcurementLotId()==null){complete=false;break;}
                lotCounts.merge(batch.get().getProcurementLotId(),1,Integer::sum);
            }
            if(!complete){result.add(line);continue;}
            var issued=procurement.allocateDelivered(trade.getId(),line.key(),line.productId(),line.specId(),lotCounts,null);
            BigDecimal cost=issued.stream().map(StockAllocation::getCost).reduce(BigDecimal.ZERO,BigDecimal::add);
            result.add(withCost(line,cost));
        }
        updateCosts(trade,result);
    }

    @Transactional public TradeView allocateCosts(UUID id,CostInput input,UUID actor){
        support.guard();FinanceTrade trade=mutable(id,input.version());
        if(trade.getSourceOrderId()!=null){
            attributeDelivered(trade,trade.getSourceOrderId());
            if(trade.getCostAmount()==null)throw bad("请先为原发货卡密批次关联真实采购入库，不能套用其他采购价格");
            return view(trade);
        }
        List<TradeLine> result=new ArrayList<>();boolean found=false;
        for(TradeLine line:items(trade)){
            if(!line.key().equals(input.lineKey())){result.add(line);continue;}
            found=true;var issued=procurement.allocateFifo(id,line.key(),line.productId(),line.specId(),line.quantity(),input.lotIds(),actor);
            result.add(withCost(line,issued.stream().map(StockAllocation::getCost).reduce(BigDecimal.ZERO,BigDecimal::add)));
        }
        if(!found)throw bad("交易商品行不存在");invalidate(trade);updateCosts(trade,result);
        support.audit("trade.inventory_cost",id,null,input,null,actor);return view(trade);
    }
    private TradeLine withCost(TradeLine line,BigDecimal cost){return new TradeLine(line.key(),line.productId(),line.specId(),line.productTitle(),line.specName(),line.quantity(),line.unitPrice(),cost,line.quoteId());}
    private void updateCosts(FinanceTrade trade,List<TradeLine> result){
        trade.setItemsJson(support.json(result));
        trade.setCostAmount(!result.isEmpty() && result.stream().allMatch(l->l.cost()!=null)?result.stream().map(TradeLine::cost).reduce(BigDecimal.ZERO,BigDecimal::add).add(trade.getReconciliationCost()):null);trades.save(trade);
    }
    @Transactional public TradeView reconcile(UUID id,ReconcileInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"核对更正幂等标识"),reason=text(input.reason(),500,"更正原因");
        BigDecimal income=amount(input.incomeDelta(),true),cost=amount(input.costDelta(),true);
        if(income.signum()==0 && cost.signum()==0)throw bad("请填写实际更正金额");
        String digest=support.digest(List.of(id,income,cost,reason));var previous=cash.findByIdempotencyKey(key);
        if(previous.isPresent()){if(!id.equals(previous.get().getTradeId()) || !"RECONCILE".equals(previous.get().getKind()) || !digest.equals(previous.get().getReference()))throw bad("核对更正标识与原请求不同");return detail(id);}
        var trade=mutable(id,input.version());String before=support.json(trade);
        trade.setReceivedAmount(amount(trade.getReceivedAmount().add(income),false));
        if(cost.signum()!=0){if(trade.getCostAmount()==null)throw bad("请先关联真实采购成本，再登记采购差额");trade.setCostAmount(amount(trade.getCostAmount().add(cost),false));trade.setReconciliationCost(amount(trade.getReconciliationCost().add(cost),true));}
        invalidate(trade);if(income.signum()!=0)trade.setReceiptFinal(false);trades.save(trade);
        var record=new FinanceCashMovement();record.setTradeId(id);record.setKind("RECONCILE");record.setAmount(income);record.setHappenedOn(time.today());record.setReference(digest);record.setReason(reason);record.setIdempotencyKey(key);record.setCreatedBy(actor);cash.save(record);
        support.audit("trade.reconcile",id,before,input,reason,actor);return view(trade);
    }
    @Transactional public TradeView changeCompany(UUID id,CompanyChangeInput input,UUID actor){
        support.guard();var trade=mutable(id,input.version());text(input.reason(),500,"归属更正原因");
        if(trade.getSourceOrderId()!=null)throw bad("平台订单保留成交时企业，不能在财务台账改写");
        var company=companies.findById(input.companyId()).filter(Company::isEnabled).orElseThrow(()->bad("企业不存在或已停用"));
        if(trades.findByCompanyIdAndExternalReferenceIgnoreCase(company.getId(),trade.getExternalReference()).filter(t->!id.equals(t.getId())).isPresent())throw bad("目标企业已存在此外部单号");
        String before=support.json(trade);trade.setCompanyId(company.getId());trade.setCompanyName(company.getName());trade.setCustomerName(company.getName());trade.setSourceKey("OFFLINE:"+company.getId()+":"+trade.getExternalReference().toLowerCase(Locale.ROOT));
        invalidate(trade);trades.save(trade);support.audit("trade.company",id,before,input,input.reason(),actor);return view(trade);
    }
    @Transactional public TradeView recordReceipt(UUID id,ReceiptInput input,UUID actor){
        support.guard();String key=text(input.idempotencyKey(),128,"收款幂等标识");BigDecimal value=amount(input.amount(),false);date(input.happenedOn());
        if(value.signum()<=0)throw bad("收款金额必须大于零");
        var previous=cash.findByIdempotencyKey(key);
        if(previous.isPresent()){
            if(!id.equals(previous.get().getTradeId()) || value.compareTo(previous.get().getAmount())!=0 || !input.happenedOn().equals(previous.get().getHappenedOn()))throw bad("收款幂等标识与原记录不同");
            return detail(id);
        }
        FinanceTrade trade=mutable(id,input.version());
        if(trade.getSourceOrderId()!=null)throw bad("线上付款已自动记入；收入纠正请通过关联调整记录处理");
        BigDecimal received=amount(trade.getReceivedAmount().add(value),false);
        if(input.finalReceipt() && received.compareTo(trade.getDueAmount())!=0)text(input.reason(),500,"最终实收差异说明");
        FinanceCashMovement row=new FinanceCashMovement();row.setTradeId(id);row.setKind("RECEIPT");row.setAmount(value);row.setHappenedOn(input.happenedOn());row.setIdempotencyKey(key);row.setReason(input.reason());row.setCreatedBy(actor);cash.save(row);
        invalidate(trade);trade.setReceivedAmount(received);trade.setReceiptFinal(input.finalReceipt());trades.save(trade);
        support.audit("trade.receipt",id,null,input,input.reason(),actor);return view(trade);
    }
    @Transactional public TradeView confirm(UUID id,Long expected,UUID actor){
        support.guard();FinanceTrade trade=mutable(id,expected);
        if(!trade.isReceiptFinal())throw bad("客户收入尚未最终确认，部分收款不能提前分成");
        if(trade.getCostAmount()==null)throw bad("存在待补采购成本，不能按零成本确认利润");
        trade.setStatus("CONFIRMED");trade.setConfirmedAt(time.now());trades.save(trade);
        support.audit("trade.confirm",id,null,Map.of("income",trade.getReceivedAmount(),"cost",trade.getCostAmount()),null,actor);return view(trade);
    }
    @Transactional public Map<String,Object> confirmBatch(List<ConfirmInput> input,UUID actor){
        support.guard();if(input==null || input.isEmpty() || input.size()>1000)throw bad("每次确认1到1000笔交易");
        Set<UUID> ids=new HashSet<>();
        for(var row:input){if(row==null || !ids.add(row.id()))throw bad("交易不得重复");var trade=mutable(row.id(),row.version());if(!trade.isReceiptFinal() || trade.getCostAmount()==null)throw bad("批次中有未结清实收或待补成本的交易，本次未确认任何记录");}
        for(var row:input)confirm(row.id(),row.version(),actor);
        return Map.of("count",input.size());
    }
    @Transactional public TradeView finalizeIncome(UUID id,FinalIncomeInput input,UUID actor){
        support.guard();FinanceTrade trade=mutable(id,input.version());
        if(trade.getReceivedAmount().compareTo(trade.getDueAmount())!=0)text(input.reason(),500,"最终实收差异说明");
        invalidate(trade);trade.setReceiptFinal(true);trades.save(trade);support.audit("trade.final_income",id,null,Map.of("received",trade.getReceivedAmount()),input.reason(),actor);return view(trade);
    }
    @Transactional public TradeView referrer(UUID id,ReferrerInput input,UUID actor){
        support.guard();FinanceTrade trade=mutable(id,input.version());validatePerson(input.referrerId());text(input.reason(),500,"修改原因");UUID before=trade.getReferrerId();
        invalidate(trade);trade.setReferrerId(input.referrerId());trades.save(trade);support.audit("trade.referrer",id,before,input.referrerId(),input.reason(),actor);return view(trade);
    }
    private FinanceTrade mutable(UUID id,Long expected){
        FinanceTrade trade=trades.lockById(id).orElseThrow(()->bad("交易不存在"));version(trade.getVersion(),expected);
        if("SETTLED".equals(trade.getStatus()))throw bad("已结算交易须使用关联调整，不能直接覆盖");return trade;
    }
    private void invalidate(FinanceTrade trade){trade.setStatus("DRAFT");trade.setConfirmedAt(null);}
    private void validatePerson(UUID id){if(id!=null)people.findById(id).filter(FinancePerson::isEnabled).orElseThrow(()->bad("推荐人不存在或已停用"));}
    private String fingerprint(OfflineInput input){
        try{
            String normalized=support.json(Arrays.asList(input.companyId(),input.externalReference()==null?null:input.externalReference().trim().toLowerCase(Locale.ROOT),input.businessDate(),input.items(),input.referrerId(),input.note()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("财务请求校验失败",e);}
    }
    public Map<String,Object> list(LocalDate start,LocalDate end,UUID companyId,String source,String status,String keyword,int page,int size){
        if(start==null || end==null || start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>365)throw bad("请选择不超过366天的有效日期范围");
        if(page<1 || size<1 || size>100)throw bad("分页无效");String search=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);if(search.length()>100)throw bad("搜索内容最多100字符");
        var rows=trades.findByBusinessDateBetweenOrderByBusinessDateDescCreatedAtDesc(start,end).stream()
            .filter(t->companyId==null || companyId.equals(t.getCompanyId())).filter(t->source==null || source.isBlank() || source.equals(t.getSource()))
            .filter(t->status==null || status.isBlank() || status.equals(t.getStatus()))
            .filter(t->search.isEmpty() || (t.getId()+" "+t.getCompanyName()+" "+t.getExternalReference()+" "+t.getCustomerName()+" "+t.getItemsJson()).toLowerCase(Locale.ROOT).contains(search)).toList();
        Map<UUID,List<FinanceAdjustment>> byTrade=new HashMap<>();
        if(!rows.isEmpty())for(var change:adjustments.findByTradeIdIn(rows.stream().map(FinanceTrade::getId).toList()))byTrade.computeIfAbsent(change.getTradeId(),k->new ArrayList<>()).add(change);
        BigDecimal received=BigDecimal.ZERO,cost=BigDecimal.ZERO,profit=BigDecimal.ZERO,due=BigDecimal.ZERO;long pending=0;
        for(var row:rows){var changes=byTrade.getOrDefault(row.getId(),List.of());BigDecimal income=effective(row,changes,false),actualCost=effective(row,changes,true);
            received=received.add(income);if(!row.isReceiptFinal())due=due.add(row.getDueAmount().subtract(income).max(BigDecimal.ZERO));if(actualCost==null)pending++;
            if(!"DRAFT".equals(row.getStatus())){cost=cost.add(actualCost);profit=profit.add(income.subtract(actualCost));}}
        int from=(int)Math.min((long)(page-1)*size,rows.size()),to=Math.min(from+size,rows.size());
        var summaries=rows.subList(from,to).stream().map(row->summary(row,byTrade.getOrDefault(row.getId(),List.of()))).toList();
        return Map.of("list",summaries,"pagination",Map.of("page",page,"page_size",size,"total",rows.size()),"totals",Map.of("received",received,"confirmed_cost",cost,"confirmed_profit",profit,"receivable",due,"missing_cost_count",pending));
    }
    private Map<String,Object> summary(FinanceTrade trade,List<FinanceAdjustment> changes){
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",trade.getId());row.put("source_order_id",trade.getSourceOrderId());row.put("source",trade.getSource());row.put("company_id",trade.getCompanyId());row.put("company_name",trade.getCompanyName());row.put("customer_name",trade.getCustomerName());row.put("external_reference",trade.getExternalReference());
        row.put("business_date",trade.getBusinessDate());row.put("due_amount",trade.getDueAmount());row.put("received_amount",effective(trade,changes,false));row.put("cost_amount",effective(trade,changes,true));row.put("receipt_final",trade.isReceiptFinal());
        row.put("original_received_amount",trade.getReceivedAmount());row.put("original_cost_amount",trade.getCostAmount());row.put("items",items(trade));row.put("status",trade.getStatus());row.put("confirmed_at",trade.getConfirmedAt());row.put("settlement_id",trade.getSettlementId());row.put("referrer_id",effectiveReferrer(trade,changes));row.put("has_adjustments",!changes.isEmpty());row.put("version",trade.getVersion());return row;
    }
    public Map<String,Object> previewImport(List<OfflineInput> input){
        if(input==null || input.isEmpty() || input.size()>1000)throw bad("每次导入1到1000笔企业交易");
        List<Map<String,Object>> results=new ArrayList<>();Set<String> seen=new HashSet<>();int errors=0;
        for(int i=0;i<input.size();i++){
            try{var row=input.get(i);
                if(row.sourceOrderId()!=null){validateLinkedInput(row);
                    var order=orders.findById(row.sourceOrderId()).orElseThrow(()->bad("平台来源不存在"));
                    if(row.companyId()==null || !row.companyId().equals(order.getCompanyId()) || order.getPaidAt()==null || !List.of(OrderStatus.PAID,OrderStatus.DELIVERED).contains(order.getStatus()))throw bad("平台来源企业不符或尚未支付");
                    String external=text(row.externalReference(),120,"企业外部单号");var conflict=trades.findByCompanyIdAndExternalReferenceIgnoreCase(row.companyId(),external);
                    if(conflict.isPresent() && !order.getId().equals(conflict.get().getSourceOrderId()))throw bad("企业单号已关联其他交易");
                    if(!seen.add("ONLINE:"+order.getId()))throw bad("同一平台订单不能在文件中重复关联");
                    results.add(Map.of("row",i+1,"valid",true,"duplicate",true,"amount",order.getActualAmount()==null?BigDecimal.ZERO:order.getActualAmount()));continue;
                }
                var prepared=prepare(row);String source="OFFLINE:"+row.companyId()+":"+row.externalReference().trim().toLowerCase(Locale.ROOT);
                if(!seen.add(source))throw bad("文件中企业外部单号重复");var old=trades.findBySourceKey(source);
                if(old.isPresent() && !fingerprint(row).equals(old.get().getCreationFingerprint()))throw bad("此企业单号已存在，内容与原记录不同");
                results.add(Map.of("row",i+1,"valid",true,"duplicate",old.isPresent(),"amount",prepared.due()));
            }catch(com.orionkey.exception.BusinessException e){errors++;results.add(Map.of("row",i+1,"valid",false,"error",e.getMessage()));}
        }
        return Map.of("rows",results,"error_count",errors,"can_commit",errors==0);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public byte[] export(LocalDate start,LocalDate end,UUID company,String source,String status,String keyword){
        StringBuilder csv=new StringBuilder("\uFEFF业务日期,来源,交易号,平台订单号,企业,客户,外部单号,商品规格,数量,成交应收,实收,采购成本,已确认利润,财务状态,有后续调整\r\n");
        int page=1;long total=0;
        do{
            var data=list(start,end,company,source,status,keyword,page,100);total=((Number)((Map<?,?>)data.get("pagination")).get("total")).longValue();
            if(total>10000)throw bad("最多导出10000笔，请缩小筛选范围");
            for(Map<String,Object> row:(List<Map<String,Object>>)data.get("list")){
                var items=(List<TradeLine>)row.get("items");String title=items.stream().map(i->i.productTitle()+(i.specName()==null?"":" / "+i.specName())).collect(java.util.stream.Collectors.joining("；"));
                for(Object value:Arrays.asList(row.get("business_date"),row.get("source"),row.get("id"),row.get("source_order_id"),row.get("company_name"),row.get("customer_name"),row.get("external_reference"),title))csv.append(FinanceCsv.cell(value)).append(',');
                csv.append(items.stream().mapToInt(TradeLine::quantity).sum()).append(',');
                for(String field:List.of("due_amount","received_amount","cost_amount")){Object value=row.get(field);if(value!=null)csv.append(((BigDecimal)value).toPlainString());csv.append(',');}
                if(!"DRAFT".equals(row.get("status")))csv.append(((BigDecimal)row.get("received_amount")).subtract((BigDecimal)row.get("cost_amount")).toPlainString());
                csv.append(',').append(FinanceCsv.cell(row.get("status"))).append(',').append(row.get("has_adjustments")).append("\r\n");
            }
            page++;
        }while((long)(page-1)*100<total);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Transactional public Map<String,Object> commitImport(List<OfflineInput> input,UUID actor){
        support.guard();var preview=previewImport(input);if(((Number)preview.get("error_count")).intValue()!=0)throw bad("导入预览有错误，请修正后再提交");
        List<UUID> ids=new ArrayList<>();for(var row:input)ids.add(createOffline(row,actor).trade().getId());return Map.of("ids",ids,"count",ids.size());
    }
}

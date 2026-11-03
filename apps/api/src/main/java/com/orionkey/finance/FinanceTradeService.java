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
    public record OfflineInput(UUID companyId,String externalReference,LocalDate businessDate,List<ItemInput> items,UUID referrerId,String note,String idempotencyKey) {}
    public record TradeLine(String key,UUID productId,UUID specId,String productTitle,String specName,int quantity,BigDecimal unitPrice,BigDecimal cost,UUID quoteId) {}
    public record TradeView(FinanceTrade trade,List<TradeLine> items,List<StockAllocation> allocations,List<FinanceCashMovement> cash,List<FinanceAudit> history) {}
    public record ReceiptInput(BigDecimal amount,LocalDate happenedOn,boolean finalReceipt,String reason,String idempotencyKey,Long version) {}
    public record CostInput(String lineKey,List<UUID> lotIds,Long version) {}
    public record ReferrerInput(UUID referrerId,String reason,Long version) {}

    public List<TradeLine> items(FinanceTrade trade){
        try{return mapper.readValue(trade.getItemsJson(),new TypeReference<List<TradeLine>>(){});}catch(Exception e){throw new IllegalStateException("交易快照损坏，不能继续核算",e);}
    }
    public TradeView detail(UUID id){return view(trades.findById(id).orElseThrow(()->bad("财务交易不存在")));}
    private TradeView view(FinanceTrade trade){return new TradeView(trade,items(trade),allocations.findByTradeId(trade.getId()),cash.findByTradeIdOrderByCreatedAtAsc(trade.getId()),audits.findByTargetIdOrderByCreatedAtDesc(trade.getId()));}

    @Transactional public TradeView createOffline(OfflineInput input,UUID actor){
        support.guard();String fingerprint=fingerprint(input);
        String external=text(input.externalReference(),120,"企业外部单号");String key=text(input.idempotencyKey(),128,"幂等标识");
        String sourceKey="OFFLINE:"+input.companyId()+":"+external.toLowerCase(Locale.ROOT);
        var previous=trades.findBySourceKey(sourceKey).or(()->trades.findByIdempotencyKey(key));
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
    private record Prepared(Company company,List<TradeLine> items,BigDecimal due) {}
    private Prepared prepare(OfflineInput input){
        date(input.businessDate());String external=text(input.externalReference(),120,"企业外部单号");
        if(input.companyId()==null)throw bad("请选择企业");
        Company company=companies.findById(input.companyId()).filter(Company::isEnabled).orElseThrow(()->bad("企业不存在或已停用"));
        if(input.items()==null || input.items().isEmpty() || input.items().size()>100)throw bad("交易须包含1到100行商品");
        validatePerson(input.referrerId());if(input.note()!=null && input.note().length()>500)throw bad("备注最多500字符");
        List<TradeLine> result=new ArrayList<>();BigDecimal due=BigDecimal.ZERO;
        for(ItemInput entry:input.items()){
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
            trade.setBusinessDate(order.getPaidAt().toLocalDate());BigDecimal received=amount(order.getActualAmount()==null?BigDecimal.ZERO:order.getActualAmount(),false);
            trade.setDueAmount(received);trade.setReceivedAmount(received);trade.setReceiptFinal(true);
            List<TradeLine> snapshot=orderItems.findByOrderId(orderId).stream().map(i->new TradeLine(i.getId().toString(),i.getProductId(),i.getSpecId(),i.getProductTitle(),i.getSpecName(),i.getQuantity(),i.getUnitPrice(),null,i.getCompanyQuoteId())).toList();
            trade.setItemsJson(support.json(snapshot));trades.save(trade);
            FinanceCashMovement receipt=new FinanceCashMovement();receipt.setTradeId(trade.getId());receipt.setKind("RECEIPT");receipt.setAmount(received);receipt.setHappenedOn(trade.getBusinessDate());receipt.setIdempotencyKey("online-receipt:"+orderId);receipt.setReference(order.getEpayTradeNo());cash.save(receipt);
            support.audit("trade.sync_order",trade.getId(),null,Map.of("source_order_id",orderId),null,null);
        }
        if("DRAFT".equals(trade.getStatus()) && order.getStatus()==OrderStatus.DELIVERED)attributeDelivered(trade,orderId);
        return view(trade);
    }
    @Transactional public Map<String,Object> syncPaidOrders(){
        support.guard();int count=0;
        for(int pass=0;pass<50;pass++){
            var batch=trades.unsynced(PageRequest.of(0,200));if(batch.isEmpty())break;
            for(Order order:batch){syncOrder(order.getId());count++;}
        }
        // Also repair previously missing cost links, without rewriting confirmed/settled snapshots.
        for(FinanceTrade trade:trades.findByStatusOrderByConfirmedAtAsc("DRAFT"))if(trade.getSourceOrderId()!=null && trade.getCostAmount()==null)syncOrder(trade.getSourceOrderId());
        return Map.of("synced",count);
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
        trade.setCostAmount(!result.isEmpty() && result.stream().allMatch(l->l.cost()!=null)?result.stream().map(TradeLine::cost).reduce(BigDecimal.ZERO,BigDecimal::add):null);trades.save(trade);
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
        BigDecimal received=BigDecimal.ZERO,cost=BigDecimal.ZERO,profit=BigDecimal.ZERO,due=BigDecimal.ZERO;long pending=0;
        for(var row:rows){received=received.add(row.getReceivedAmount());due=due.add(row.getDueAmount().subtract(row.getReceivedAmount()).max(BigDecimal.ZERO));if(row.getCostAmount()==null)pending++;
            if(!"DRAFT".equals(row.getStatus())){cost=cost.add(row.getCostAmount());profit=profit.add(row.getReceivedAmount().subtract(row.getCostAmount()));}}
        int from=(int)Math.min((long)(page-1)*size,rows.size()),to=Math.min(from+size,rows.size());
        return Map.of("list",rows.subList(from,to),"pagination",Map.of("page",page,"page_size",size,"total",rows.size()),"totals",Map.of("received",received,"confirmed_cost",cost,"confirmed_profit",profit,"receivable",due,"missing_cost_count",pending));
    }
    public Map<String,Object> previewImport(List<OfflineInput> input){
        if(input==null || input.isEmpty() || input.size()>1000)throw bad("每次导入1到1000笔企业交易");
        List<Map<String,Object>> results=new ArrayList<>();Set<String> seen=new HashSet<>();int errors=0;
        for(int i=0;i<input.size();i++){
            try{var row=input.get(i);var prepared=prepare(row);String source="OFFLINE:"+row.companyId()+":"+row.externalReference().trim().toLowerCase(Locale.ROOT);
                if(!seen.add(source))throw bad("文件中企业外部单号重复");var old=trades.findBySourceKey(source);
                if(old.isPresent() && !fingerprint(row).equals(old.get().getCreationFingerprint()))throw bad("此企业单号已存在，内容与原记录不同");
                results.add(Map.of("row",i+1,"valid",true,"duplicate",old.isPresent(),"amount",prepared.due()));
            }catch(com.orionkey.exception.BusinessException e){errors++;results.add(Map.of("row",i+1,"valid",false,"error",e.getMessage()));}
        }
        return Map.of("rows",results,"error_count",errors,"can_commit",errors==0);
    }
    @Transactional public Map<String,Object> commitImport(List<OfflineInput> input,UUID actor){
        support.guard();var preview=previewImport(input);if(((Number)preview.get("error_count")).intValue()!=0)throw bad("导入预览有错误，请修正后再提交");
        List<UUID> ids=new ArrayList<>();for(var row:input)ids.add(createOffline(row,actor).trade().getId());return Map.of("ids",ids,"count",ids.size());
    }
}

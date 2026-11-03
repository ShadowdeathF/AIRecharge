package com.orionkey.finance;

import com.orionkey.common.PageResult;
import com.orionkey.entity.*;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import com.orionkey.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.orionkey.finance.FinanceSupport.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ProcurementService {
    private final PurchaseOrderRepository purchases;
    private final PurchaseLineRepository lines;
    private final PurchaseLotRepository lots;
    private final StockAllocationRepository allocations;
    private final ProductRepository products;
    private final ProductSpecRepository specs;
    private final CardImportBatchRepository cardBatches;
    private final FinanceSupport support;

    public record LineInput(UUID productId,UUID specId,int quantity) {}
    public record PurchaseInput(String supplier,LocalDate purchasedOn,String kind,List<LineInput> lines,String idempotencyKey,String note) {}
    public record ReceiveInput(UUID lineId,int quantity,BigDecimal totalCost,String idempotencyKey,UUID cardImportBatchId,LocalDate receivedOn) {}
    public record PurchaseView(PurchaseOrder purchase,List<PurchaseLine> lines,List<PurchaseLot> lots) {}

    public PageResult<PurchaseView> list(int page,int size) {
        if(page<1 || size<1 || size>100) throw bad("分页参数无效");
        var data=purchases.findAll(PageRequest.of(page-1,size,Sort.by("createdAt").descending()));
        return PageResult.of(data,data.stream().map(p->detail(p.getId())).toList());
    }
    public PurchaseView detail(UUID id) {
        return new PurchaseView(purchases.findById(id).orElseThrow(()->bad("采购单不存在")),lines.findByPurchaseIdOrderByCreatedAtAsc(id),lots.findByPurchaseIdOrderByCreatedAtAsc(id));
    }
    public List<PurchaseLot> stock() { return lots.findAll(Sort.by("receivedOn").descending().and(Sort.by("id"))); }
    @Transactional
    public PurchaseView createPurchase(PurchaseInput request,UUID actor) {
        String key=text(request.idempotencyKey(),128,"幂等标识");
        String supplier=text(request.supplier(),120,"供应商");
        date(request.purchasedOn());
        if(!List.of("MONTHLY","SUPPLEMENT").contains(request.kind())) throw bad("采购类型必须为月度采购或补采");
        if(request.lines()==null || request.lines().isEmpty() || request.lines().size()>200) throw bad("采购单须有1到200行商品");
        var existing=purchases.findByIdempotencyKey(key);
        if(existing.isPresent()) {
            PurchaseView original=detail(existing.get().getId());
            boolean same=original.purchase().getSupplier().equals(supplier) && original.purchase().getKind().equals(request.kind())
                && original.purchase().getPurchasedOn().equals(request.purchasedOn()) && original.lines().size()==request.lines().size();
            for(int i=0;same && i<request.lines().size();i++) {
                var a=original.lines().get(i);var b=request.lines().get(i);
                same=a.getProductId().equals(b.productId()) && Objects.equals(a.getSpecId(),b.specId()) && a.getQuantity()==b.quantity();
            }
            if(!same) throw bad("幂等标识已用于不同的采购请求");
            return original;
        }
        if(request.note()!=null && request.note().length()>500) throw bad("采购备注最多500字符");
        PurchaseOrder purchase=new PurchaseOrder();purchase.setSupplier(supplier);purchase.setKind(request.kind());purchase.setPurchasedOn(request.purchasedOn());
        purchase.setIdempotencyKey(key);purchase.setNote(request.note());purchase.setCreatedBy(actor);purchases.save(purchase);
        for(LineInput input:request.lines()) {
            Product product=sku(input.productId(),input.specId());
            PurchaseLine line=new PurchaseLine();line.setPurchaseId(purchase.getId());line.setProductId(product.getId());line.setSpecId(input.specId());
            line.setProductTitle(product.getTitle());line.setQuantity(quantity(input.quantity()));
            if(input.specId()!=null) line.setSpecName(specs.findById(input.specId()).orElseThrow().getName());
            lines.save(line);
        }
        support.audit("purchase.create",purchase.getId(),null,request,null,actor);
        return detail(purchase.getId());
    }

    @Transactional
    public PurchaseLot receive(UUID purchaseId,ReceiveInput request,UUID actor) {
        String key=text(request.idempotencyKey(),128,"入库幂等标识");int qty=quantity(request.quantity());
        BigDecimal cost=amount(request.totalCost(),false);LocalDate receivedOn=date(request.receivedOn());
        PurchaseOrder purchase=purchases.lockById(purchaseId).orElseThrow(()->bad("采购单不存在"));
        var previous=lots.findByIdempotencyKey(key);
        if(previous.isPresent()) {
            PurchaseLot lot=previous.get();
            if(!lot.getPurchaseId().equals(purchaseId) || !lot.getPurchaseLineId().equals(request.lineId()) || lot.getQuantity()!=qty
                || lot.getTotalCost().compareTo(cost)!=0 || !lot.getReceivedOn().equals(receivedOn)
                || (request.cardImportBatchId()!=null && !Objects.equals(lot.getCardImportBatchId(),request.cardImportBatchId()))) throw bad("入库幂等标识与原请求不同");
            return lot;
        }
        PurchaseLine line=lines.findById(request.lineId()).filter(l->l.getPurchaseId().equals(purchaseId)).orElseThrow(()->bad("采购明细不属于此采购单"));
        if((long)line.getReceivedQuantity()+qty>line.getQuantity()) throw bad("入库数量超过本采购行未入库数量");
        if(receivedOn.isBefore(purchase.getPurchasedOn())) throw bad("入库日期不能早于采购日期");
        PurchaseLot lot=new PurchaseLot();lot.setPurchaseId(purchaseId);lot.setPurchaseLineId(line.getId());lot.setProductId(line.getProductId());lot.setSpecId(line.getSpecId());
        lot.setQuantity(qty);lot.setTotalCost(cost);lot.setReceivedOn(receivedOn);lot.setIdempotencyKey(key);lot.setCreatedBy(actor);lots.save(lot);
        if(request.cardImportBatchId()!=null) linkCardBatch(lot.getId(),request.cardImportBatchId(),actor);
        line.setReceivedQuantity(line.getReceivedQuantity()+qty);lines.save(line);
        boolean done=lines.findByPurchaseIdOrderByCreatedAtAsc(purchaseId).stream().allMatch(l->l.getReceivedQuantity()==l.getQuantity());
        purchase.setStatus(done?"RECEIVED":"PARTIAL");purchases.save(purchase);
        support.audit("purchase.receive",lot.getId(),null,request,null,actor);
        return lot;
    }

    @Transactional
    public void linkCardBatch(UUID lotId,UUID batchId,UUID actor) {
        PurchaseLot lot=lots.lockById(lotId).orElseThrow(()->bad("采购入库批次不存在"));
        CardImportBatch batch=cardBatches.lockById(batchId).orElseThrow(()->bad("卡密入库批次不存在"));
        if(!lot.getProductId().equals(batch.getProductId()) || !Objects.equals(lot.getSpecId(),batch.getSpecId()) || lot.getQuantity()!=batch.getSuccessCount())
            throw bad("卡密批次的商品、规格和成功数量须与采购入库一致");
        if((lot.getCardImportBatchId()!=null && !lot.getCardImportBatchId().equals(batchId)) || (batch.getProcurementLotId()!=null && !batch.getProcurementLotId().equals(lotId)))
            throw bad("采购入库或卡密批次已经关联其他来源");
        if(lot.getIssuedQuantity()!=0 && lot.getCardImportBatchId()==null) throw bad("已按数量出库的采购批次不能再次作为卡密库存入库");
        batch.setProcurementLotId(lotId);lot.setCardImportBatchId(batchId);cardBatches.save(batch);lots.save(lot);
        support.audit("purchase.link_cards",lotId,null,Map.of("card_import_batch_id",batchId),null,actor);
    }

    /** Explicit offline inventory issue. Card-backed lots are attributed only to the keys actually delivered. */
    @Transactional
    public List<StockAllocation> allocateFifo(UUID tradeId,String lineKey,UUID productId,UUID specId,int qty,List<UUID> selected,UUID actor) {
        quantity(qty);text(lineKey,80,"交易行标识");sku(productId,specId);
        List<PurchaseLot> candidates=lots.lockSku(productId,specId);
        var existing=allocations.findByTradeIdAndLineKeyOrderByCreatedAtAsc(tradeId,lineKey);
        if(!existing.isEmpty()) {
            if(existing.stream().mapToInt(StockAllocation::getQuantity).sum()!=qty) throw bad("已出库的交易行数量不能覆盖修改");
            return existing;
        }
        if(selected==null) selected=List.of();
        Set<UUID> ids=new HashSet<>(selected);
        if(ids.size()!=selected.size()) throw bad("采购批次不得重复");
        Set<UUID> validIds=new HashSet<>();candidates.forEach(l->validIds.add(l.getId()));
        if(!validIds.containsAll(ids)) throw bad("指定采购批次与商品规格不符");
        candidates=candidates.stream().filter(l->l.getCardImportBatchId()==null && (ids.isEmpty() || ids.contains(l.getId()))).toList();
        if(candidates.stream().mapToLong(l->l.getQuantity()-l.getIssuedQuantity()).sum()<qty) throw bad("采购可出库数量不足，请先补采并入库");
        int remaining=qty;List<StockAllocation> result=new ArrayList<>();
        for(PurchaseLot lot:candidates) {
            int take=Math.min(remaining,lot.getQuantity()-lot.getIssuedQuantity());
            if(take>0){result.add(issue(lot,tradeId,lineKey,take,actor));remaining-=take;}
            if(remaining==0) break;
        }
        return result;
    }

    @Transactional
    public List<StockAllocation> allocateDelivered(UUID tradeId,String lineKey,UUID productId,UUID specId,Map<UUID,Integer> actualLots,UUID actor) {
        var candidates=lots.lockSku(productId,specId);
        var existing=allocations.findByTradeIdAndLineKeyOrderByCreatedAtAsc(tradeId,lineKey);
        if(!existing.isEmpty()) return existing;
        List<StockAllocation> result=new ArrayList<>();
        for(PurchaseLot lot:candidates) {
            Integer qty=actualLots.get(lot.getId());
            if(qty!=null) { if(lot.getCardImportBatchId()==null) throw bad("已发卡密缺少采购批次关联");result.add(issue(lot,tradeId,lineKey,quantity(qty),actor)); }
        }
        if(result.size()!=actualLots.size()) throw bad("已发卡密的采购商品规格不匹配");
        return result;
    }
    private StockAllocation issue(PurchaseLot lot,UUID tradeId,String lineKey,int qty,UUID actor) {
        BigDecimal cost;
        try{cost=ProcurementMath.issueCost(lot.getTotalCost(),lot.getQuantity(),lot.getIssuedQuantity(),qty);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
        lot.setIssuedQuantity(lot.getIssuedQuantity()+qty);lot.setIssuedCost(lot.getIssuedCost().add(cost));lots.save(lot);
        StockAllocation issue=new StockAllocation();issue.setTradeId(tradeId);issue.setLineKey(lineKey);issue.setLotId(lot.getId());issue.setQuantity(qty);issue.setCost(cost);issue.setCreatedBy(actor);allocations.save(issue);
        support.audit("inventory.issue",issue.getId(),null,Map.of("trade_id",tradeId,"line_key",lineKey,"lot_id",lot.getId(),"quantity",qty,"cost",cost),null,actor);
        return issue;
    }
    private Product sku(UUID productId,UUID specId) {
        if(productId==null) throw bad("请选择商品");
        Product product=products.findById(productId).filter(p->p.getIsDeleted()==0).orElseThrow(()->bad("商品不存在"));
        if(specId!=null) specs.findById(specId).filter(s->s.getProductId().equals(productId) && s.getIsDeleted()==0).orElseThrow(()->bad("商品规格不存在或不属于此商品"));
        if(product.isSpecEnabled() && specId==null) throw bad("此商品必须选择具体规格");
        return product;
    }
}

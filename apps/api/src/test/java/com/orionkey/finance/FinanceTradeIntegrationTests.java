package com.orionkey.finance;

import com.orionkey.entity.*;
import com.orionkey.constant.*;
import com.orionkey.exception.BusinessException;
import com.orionkey.finance.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class FinanceTradeIntegrationTests {
    @Autowired FinanceTradeService trades;
    @Autowired ProcurementService procurement;
    @Autowired CompanyPricingService companies;
    @Autowired EntityManager em;
    private final UUID actor=UUID.randomUUID();
    private Product product(){Product p=new Product();p.setTitle("Finance SKU");p.setCategoryId(UUID.randomUUID());p.setBasePrice(new BigDecimal("135.00"));em.persist(p);return p;}
    private ProcurementService.PurchaseView purchase(Product p,int qty){return procurement.createPurchase(new ProcurementService.PurchaseInput("Vendor",LocalDate.now(),"SUPPLEMENT",List.of(new ProcurementService.LineInput(p.getId(),null,qty)),UUID.randomUUID().toString(),""),actor);}
    private FinanceTradeService.OfflineInput input(UUID company,Product p,String external){return new FinanceTradeService.OfflineInput(company,external,LocalDate.now(),List.of(new FinanceTradeService.ItemInput(p.getId(),null,2,new BigDecimal("120.00"),"contract")),null,"",UUID.randomUUID().toString());}
    @Test void incompleteIncomeAndUnknownCostsNeverLookProfitable(){
        var p=product();var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Trade-"+UUID.randomUUID(),true,""),actor);
        var trade=trades.createOffline(input(c.getId(),p,"INV-1"),actor);
        assertEquals(new BigDecimal("240.00"),trade.trade().getDueAmount());assertNull(trade.trade().getCostAmount());
        assertThrows(BusinessException.class,()->trades.confirm(trade.trade().getId(),trade.trade().getVersion(),actor));
    }
    @Test void finalReceiptsAndProcurementIssueProduceConfirmedProfit(){
        var p=product();var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Paid-"+UUID.randomUUID(),true,""),actor);
        var purchase=purchase(p,2);procurement.receive(purchase.purchase().getId(),new ProcurementService.ReceiveInput(purchase.lines().getFirst().getId(),2,new BigDecimal("200.00"),UUID.randomUUID().toString(),null,LocalDate.now()),actor);
        var view=trades.createOffline(input(c.getId(),p,"INV-2"),actor);UUID id=view.trade().getId();
        trades.recordReceipt(id,new FinanceTradeService.ReceiptInput(new BigDecimal("40.00"),LocalDate.now(),false,"",UUID.randomUUID().toString(),view.trade().getVersion()),actor);
        view=trades.allocateCosts(id,new FinanceTradeService.CostInput(view.items().getFirst().key(),List.of(),view.trade().getVersion()),actor);
        var partial=view;assertThrows(BusinessException.class,()->trades.confirm(id,partial.trade().getVersion(),actor));
        view=trades.recordReceipt(id,new FinanceTradeService.ReceiptInput(new BigDecimal("200.00"),LocalDate.now(),true,"",UUID.randomUUID().toString(),view.trade().getVersion()),actor);
        view=trades.confirm(id,view.trade().getVersion(),actor);
        assertEquals("CONFIRMED",view.trade().getStatus());assertEquals(new BigDecimal("40.00"),view.trade().getReceivedAmount().subtract(view.trade().getCostAmount()));
    }
    @Test void onlineSyncIsUniqueAndLegacyCostsRemainUnknown(){
        var p=product();Order order=new Order();order.setEmail("demo@example.test");order.setOrderType(OrderType.DIRECT);order.setStatus(OrderStatus.PAID);order.setPaidAt(LocalDateTime.now());order.setActualAmount(new BigDecimal("240.00"));order.setTotalAmount(new BigDecimal("240.00"));em.persist(order);
        OrderItem item=new OrderItem();item.setOrderId(order.getId());item.setProductId(p.getId());item.setProductTitle(p.getTitle());item.setQuantity(2);item.setUnitPrice(new BigDecimal("120.00"));item.setSubtotal(new BigDecimal("240.00"));em.persist(item);
        var a=trades.syncOrder(order.getId());var b=trades.syncOrder(order.getId());
        assertEquals(a.trade().getId(),b.trade().getId());assertTrue(a.trade().isReceiptFinal());assertNull(a.trade().getCostAmount());
        assertEquals(1,((List<?>)trades.list(LocalDate.now(),LocalDate.now(),null,null,null,order.getId().toString(),1,20).get("list")).size());
        assertThrows(BusinessException.class,()->trades.confirm(a.trade().getId(),a.trade().getVersion(),actor));
    }
    @Test void companyExternalReferenceAndReceiptRetriesDoNotDuplicateMoney(){
        var p=product();var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Dedup-"+UUID.randomUUID(),true,""),actor);
        var input=input(c.getId(),p,"SAME");var a=trades.createOffline(input,actor);var b=trades.createOffline(input,actor);assertEquals(a.trade().getId(),b.trade().getId());
        var receipt=new FinanceTradeService.ReceiptInput(new BigDecimal("120.00"),LocalDate.now(),false,"",UUID.randomUUID().toString(),a.trade().getVersion());
        trades.recordReceipt(a.trade().getId(),receipt,actor);var retried=trades.recordReceipt(a.trade().getId(),receipt,actor);
        assertEquals(new BigDecimal("120.00"),retried.trade().getReceivedAmount());
    }
    @Test void onlineDeliveredKeysUseTheirActualProcurementReceipt(){
        var p=product();var purchase=purchase(p,2);var lot=procurement.receive(purchase.purchase().getId(),new ProcurementService.ReceiveInput(purchase.lines().getFirst().getId(),2,new BigDecimal("210.01"),UUID.randomUUID().toString(),null,LocalDate.now()),actor);
        CardImportBatch batch=new CardImportBatch();batch.setProductId(p.getId());batch.setImportedBy(actor);batch.setSuccessCount(2);em.persist(batch);procurement.linkCardBatch(lot.getId(),batch.getId(),actor);
        Order order=new Order();order.setOrderType(OrderType.DIRECT);order.setStatus(OrderStatus.DELIVERED);order.setPaidAt(LocalDateTime.now());order.setActualAmount(new BigDecimal("240.00"));em.persist(order);
        OrderItem item=new OrderItem();item.setOrderId(order.getId());item.setProductId(p.getId());item.setProductTitle(p.getTitle());item.setQuantity(2);item.setUnitPrice(new BigDecimal("120.00"));item.setSubtotal(new BigDecimal("240.00"));em.persist(item);
        for(int i=0;i<2;i++){CardKey key=new CardKey();key.setProductId(p.getId());key.setContent("TEST-"+UUID.randomUUID());key.setImportBatchId(batch.getId());key.setOrderId(order.getId());key.setOrderItemId(item.getId());key.setStatus(CardKeyStatus.SOLD);em.persist(key);}
        var view=trades.syncOrder(order.getId());assertEquals(new BigDecimal("210.01"),view.trade().getCostAmount());
        assertEquals(lot.getId(),view.allocations().getFirst().getLotId());
    }
    @Test void batchConfirmationRejectsIncompleteRowsWithoutPartiallyConfirmingOthers(){
        var p=product();var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Batch-"+UUID.randomUUID(),true,""),actor);
        var a=trades.createOffline(input(c.getId(),p,"BATCH-1"),actor);var b=trades.createOffline(input(c.getId(),p,"BATCH-2"),actor);
        var purchase=purchase(p,2);procurement.receive(purchase.purchase().getId(),new ProcurementService.ReceiveInput(purchase.lines().getFirst().getId(),2,new BigDecimal("200.00"),UUID.randomUUID().toString(),null,LocalDate.now()),actor);
        trades.allocateCosts(a.trade().getId(),new FinanceTradeService.CostInput(a.items().getFirst().key(),List.of(),a.trade().getVersion()),actor);
        trades.recordReceipt(a.trade().getId(),new FinanceTradeService.ReceiptInput(new BigDecimal("240.00"),LocalDate.now(),true,"",UUID.randomUUID().toString(),a.trade().getVersion()),actor);
        var requests=List.of(new FinanceTradeService.ConfirmInput(a.trade().getId(),a.trade().getVersion()),new FinanceTradeService.ConfirmInput(b.trade().getId(),b.trade().getVersion()));
        assertThrows(BusinessException.class,()->trades.confirmBatch(requests,actor));
        assertEquals("DRAFT",trades.detail(a.trade().getId()).trade().getStatus());
    }
    @Test void missingOnlineActualIncomeRemainsUnconfirmedInsteadOfAssumingAFreeOrder(){
        Order order=new Order();order.setOrderType(OrderType.DIRECT);order.setStatus(OrderStatus.PAID);order.setPaidAt(LocalDateTime.now());order.setTotalAmount(new BigDecimal("135.00"));order.setActualAmount(null);em.persist(order);
        var view=trades.syncOrder(order.getId());
        assertFalse(view.trade().isReceiptFinal());assertEquals(new BigDecimal("135.00"),view.trade().getDueAmount());assertTrue(view.cash().isEmpty());
    }
    @Test void offlineEntryCanLinkAnExistingCompanyOrderWithoutCreatingAnotherIncome(){
        var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Link-"+UUID.randomUUID(),true,""),actor);
        Order order=new Order();order.setOrderType(OrderType.DIRECT);order.setCompanyId(c.getId());order.setCompanyName(c.getName());order.setStatus(OrderStatus.PAID);order.setPaidAt(LocalDateTime.now());order.setActualAmount(new BigDecimal("135.00"));em.persist(order);
        var input=new FinanceTradeService.OfflineInput(c.getId(),"LINK-1",LocalDate.now(),List.of(),null,"",UUID.randomUUID().toString(),order.getId());
        var linked=trades.createOffline(input,actor);var original=trades.syncOrder(order.getId());assertEquals(original.trade().getId(),linked.trade().getId());assertEquals(1,linked.cash().size());
        assertThrows(BusinessException.class,()->trades.createOffline(new FinanceTradeService.OfflineInput(c.getId(),"LINK-1",LocalDate.now(),List.of(),null,"",UUID.randomUUID().toString()),actor));
    }
    @Test void preSettlementIncomeCorrectionPreservesPaymentFactsAndInvalidatesConfirmation(){
        var p=product();var c=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Correct-"+UUID.randomUUID(),true,""),actor);
        var view=trades.createOffline(input(c.getId(),p,"COR-1"),actor);UUID id=view.trade().getId();
        view=trades.recordReceipt(id,new FinanceTradeService.ReceiptInput(new BigDecimal("240.00"),LocalDate.now(),true,"",UUID.randomUUID().toString(),view.trade().getVersion()),actor);
        var request=new FinanceTradeService.ReconcileInput(new BigDecimal("-10.00"),BigDecimal.ZERO,"receipt correction",UUID.randomUUID().toString(),view.trade().getVersion());
        view=trades.reconcile(id,request,actor);assertEquals(new BigDecimal("230.00"),view.trade().getReceivedAmount());assertFalse(view.trade().isReceiptFinal());assertEquals("DRAFT",view.trade().getStatus());
        assertEquals(new BigDecimal("240.00"),view.cash().stream().filter(r->"RECEIPT".equals(r.getKind())).findFirst().orElseThrow().getAmount());
        assertEquals(new BigDecimal("230.00"),trades.reconcile(id,request,actor).trade().getReceivedAmount());
    }
    @Test void historyBackfillCommitsBoundedBatchesAndOnlineImportRefusesIgnoredReferralFields(){
        for(int i=0;i<51;i++){Order order=new Order();order.setOrderType(OrderType.DIRECT);order.setStatus(OrderStatus.PAID);order.setPaidAt(LocalDateTime.now());order.setActualAmount(BigDecimal.ONE);em.persist(order);}
        var first=trades.syncPaidOrders();assertEquals(50,first.get("synced"));assertEquals(true,first.get("has_more"));
        var second=trades.syncPaidOrders();assertEquals(1,second.get("synced"));assertEquals(false,second.get("has_more"));
        assertThrows(BusinessException.class,()->trades.createOffline(new FinanceTradeService.OfflineInput(UUID.randomUUID(),"SOURCE",LocalDate.now(),List.of(),UUID.randomUUID(),"",UUID.randomUUID().toString(),UUID.randomUUID()),actor));
    }
}

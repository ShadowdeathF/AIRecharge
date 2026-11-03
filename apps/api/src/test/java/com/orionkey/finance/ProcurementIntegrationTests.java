package com.orionkey.finance;

import com.orionkey.entity.Product;
import com.orionkey.exception.BusinessException;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class ProcurementIntegrationTests {
    @Autowired ProcurementService service;
    @Autowired ProcurementReceivingService receiving;
    @Autowired PurchaseLotRepository lots;
    @Autowired EntityManager em;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.orionkey.service.AdminCardKeyService cardService;
    private final UUID actor = UUID.randomUUID();
    private Product product() {
        Product p = new Product(); p.setTitle("Purchase test"); p.setCategoryId(UUID.randomUUID()); p.setBasePrice(new BigDecimal("135.00")); em.persist(p); return p;
    }
    private ProcurementService.PurchaseView purchase(Product p,int quantity) {
        return service.createPurchase(new ProcurementService.PurchaseInput("Supplier",LocalDate.now(),"SUPPLEMENT",List.of(new ProcurementService.LineInput(p.getId(),null,quantity)),UUID.randomUUID().toString(),"test"),actor);
    }
    private PurchaseLot receive(ProcurementService.PurchaseView p,int qty,String cost,String key) {
        return service.receive(p.purchase().getId(),new ProcurementService.ReceiveInput(p.lines().getFirst().getId(),qty,new BigDecimal(cost),key,null,LocalDate.now()),actor);
    }
    @Test void supplementalQuantityOneUsesSamePurchaseWorkflow() {
        var p=purchase(product(),1); var lot=receive(p,1,"112.04","receipt-"+UUID.randomUUID());
        assertEquals(1,lot.getQuantity()); assertEquals(new BigDecimal("112.04"),lot.getTotalCost());
        assertEquals("RECEIVED",service.detail(p.purchase().getId()).purchase().getStatus());
    }
    @Test void supportsPartialReceiptsAndRefusesOverReceipt() {
        var p=purchase(product(),10); receive(p,3,"300.00","one-"+UUID.randomUUID());
        assertEquals("PARTIAL",service.detail(p.purchase().getId()).purchase().getStatus());
        assertThrows(BusinessException.class,()->receive(p,8,"800.00","two-"+UUID.randomUUID()));
    }
    @Test void receiptRetryDoesNotCreateInventoryTwice() {
        var p=purchase(product(),1); String key=UUID.randomUUID().toString();
        var first=receive(p,1,"100.00",key); var retry=receive(p,1,"100.00",key);
        assertEquals(first.getId(),retry.getId());
        assertThrows(BusinessException.class,()->receive(p,1,"101.00",key));
    }
    @Test void issuesAcrossActualLotsAndPreservesReceiptCost() {
        var product=product(); var p=purchase(product,3); var lot1=receive(p,1,"100.00",UUID.randomUUID().toString());
        var lot2=receive(p,2,"220.01",UUID.randomUUID().toString());
        var issues=service.allocateFifo(UUID.randomUUID(),"line-1",product.getId(),null,3,List.of(),actor);
        assertEquals(new BigDecimal("320.01"),issues.stream().map(StockAllocation::getCost).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(1,lots.findById(lot1.getId()).orElseThrow().getIssuedQuantity());
        assertEquals(2,lots.findById(lot2.getId()).orElseThrow().getIssuedQuantity());
        assertThrows(BusinessException.class,()->service.allocateFifo(UUID.randomUUID(),"line-2",product.getId(),null,1,List.of(),actor));
    }
    @Test void issueRetryKeepsOriginalCostAndDoesNotConsumeTwice() {
        var product=product(); var p=purchase(product,2); var lot=receive(p,2,"0.01",UUID.randomUUID().toString());
        UUID trade=UUID.randomUUID(); var first=service.allocateFifo(trade,"same-line",product.getId(),null,1,List.of(),actor);
        var retry=service.allocateFifo(trade,"same-line",product.getId(),null,1,List.of(),actor);
        assertEquals(first.getFirst().getId(),retry.getFirst().getId());
        assertEquals(1,lots.findById(lot.getId()).orElseThrow().getIssuedQuantity());
    }
    @Test void costForDifferentSkuCannotBeBorrowed() {
        var a=product(); var b=product(); var lot=receive(purchase(a,1),1,"10.00",UUID.randomUUID().toString());
        assertThrows(BusinessException.class,()->service.allocateFifo(UUID.randomUUID(),"line",b.getId(),null,1,List.of(lot.getId()),actor));
        assertEquals(0,lots.findById(lot.getId()).orElseThrow().getIssuedQuantity());
    }
    @Test void cardImportCarriesActualPurchaseLotAndCannotCreateDuplicatePhysicalStock() {
        var product=product();var lot=receive(purchase(product,1),1,"112.04",UUID.randomUUID().toString());
        var imported=cardService.importCardKeys(Map.of("product_id",product.getId().toString(),"content","TEST-PROCUREMENT-"+UUID.randomUUID(),"procurement_lot_id",lot.getId().toString()),actor);
        assertNotNull(lots.findById(lot.getId()).orElseThrow().getCardImportBatchId());
        assertThrows(BusinessException.class,()->service.allocateFifo(UUID.randomUUID(),"offline",product.getId(),null,1,List.of(lot.getId()),actor));
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
    void financeMoneyUsesDecimalStringsAcrossApiBoundary() throws Exception {
        receive(purchase(product(),1),1,"112.04",UUID.randomUUID().toString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/procurement/lots"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].total_cost").isString());
    }
    @Test void receiptAndCardImportAreOneRetriableOperation(){
        var p=purchase(product(),1);String card="TEST-ATOMIC-"+UUID.randomUUID();
        var input=new ProcurementReceivingService.Input(new ProcurementService.ReceiveInput(p.lines().getFirst().getId(),1,new BigDecimal("112.04"),UUID.randomUUID().toString(),null,LocalDate.now()),card);
        var first=receiving.receive(p.purchase().getId(),input,actor);var second=receiving.receive(p.purchase().getId(),input,actor);
        assertNotNull(first.getCardImportBatchId());assertEquals(first.getId(),second.getId());assertEquals(1,service.detail(p.purchase().getId()).lots().size());
    }
}

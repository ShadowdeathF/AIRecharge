package com.orionkey.finance;

import com.orionkey.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:finance_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000")
class FinanceConcurrencyTests {
    @Autowired ProcurementService procurement;
    @Autowired CompanyPricingService companies;
    @Autowired FinanceTradeService trades;
    @Autowired FinanceSettlementService settlements;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    private final UUID actor=UUID.randomUUID();
    private record Fixture(UUID product,UUID trade,String lineKey){}
    private Fixture fixture(boolean ready){
        return new TransactionTemplate(transactionManager).execute(status->{
            Product product=new Product();product.setTitle("Concurrency");product.setCategoryId(UUID.randomUUID());product.setBasePrice(new BigDecimal("100.00"));em.persist(product);
            var purchase=procurement.createPurchase(new ProcurementService.PurchaseInput("Concurrency",LocalDate.now(),"SUPPLEMENT",List.of(new ProcurementService.LineInput(product.getId(),null,1)),UUID.randomUUID().toString(),""),actor);
            procurement.receive(purchase.purchase().getId(),new ProcurementService.ReceiveInput(purchase.lines().getFirst().getId(),1,new BigDecimal("10.01"),UUID.randomUUID().toString(),null,LocalDate.now()),actor);
            var company=companies.saveCompany(null,new CompanyPricingService.CompanyInput("Concurrent-"+UUID.randomUUID(),true,""),actor);
            var view=trades.createOffline(new FinanceTradeService.OfflineInput(company.getId(),UUID.randomUUID().toString(),LocalDate.now(),List.of(new FinanceTradeService.ItemInput(product.getId(),null,1,new BigDecimal("100.00"),"")),null,"",UUID.randomUUID().toString()),actor);
            if(ready){
                for(var person:settlements.people())settlements.savePerson(person.getId(),new FinanceSettlementService.PersonInput(person.getName(),person.getBaseRole(),person.isEnabled(),person.getDividendWeight(),person.getVersion()),actor);
                view=trades.allocateCosts(view.trade().getId(),new FinanceTradeService.CostInput(view.items().getFirst().key(),List.of(),view.trade().getVersion()),actor);
                view=trades.recordReceipt(view.trade().getId(),new FinanceTradeService.ReceiptInput(new BigDecimal("100.00"),LocalDate.now(),true,"",UUID.randomUUID().toString(),view.trade().getVersion()),actor);
                view=trades.confirm(view.trade().getId(),view.trade().getVersion(),actor);
            }
            return new Fixture(product.getId(),view.trade().getId(),view.items().getFirst().key());
        });
    }
    private List<Boolean> race(Supplier<?> first,Supplier<?> second) throws Exception {
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(var task:List.of(first,second))results.add(executor.submit(()->{start.await();try{task.get();return true;}catch(com.orionkey.exception.BusinessException e){return false;}}));
            start.countDown();return List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();}
    }
    @Test void twoStockIssuesCannotConsumeTheSameLastUnitAndARetryKeepsItsOriginalCost() throws Exception {
        var data=fixture(false);UUID another=UUID.randomUUID();
        var result=race(()->procurement.allocateFifo(data.trade(),data.lineKey(),data.product(),null,1,List.of(),actor),()->procurement.allocateFifo(another,"second",data.product(),null,1,List.of(),actor));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        UUID winner=result.get(0)?data.trade():another;String line=result.get(0)?data.lineKey():"second";
        var retry=procurement.allocateFifo(winner,line,data.product(),null,1,List.of(),actor);assertEquals(1,retry.size());assertEquals(new BigDecimal("10.01"),retry.getFirst().getCost());
    }
    @Test void simultaneousProfitConfirmationCreatesOnlyOneOriginalSettlement() throws Exception {
        var data=fixture(true);var preview=settlements.previewProfit(new FinanceSettlementService.ProfitInput(List.of(data.trade()),"preview"));
        var result=race(()->settlements.confirmProfit(new FinanceSettlementService.ProfitInput(List.of(data.trade()),UUID.randomUUID().toString(),preview.previewToken()),actor),()->settlements.confirmProfit(new FinanceSettlementService.ProfitInput(List.of(data.trade()),UUID.randomUUID().toString(),preview.previewToken()),actor));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals("SETTLED",trades.detail(data.trade()).trade().getStatus());
    }
}

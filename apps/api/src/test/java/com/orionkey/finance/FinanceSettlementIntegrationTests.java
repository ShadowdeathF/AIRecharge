package com.orionkey.finance;

import com.orionkey.entity.Product;
import com.orionkey.exception.BusinessException;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

@SpringBootTest @Transactional
class FinanceSettlementIntegrationTests {
    @Autowired FinanceSettlementService settlements;
    @Autowired FinanceTradeService trades;
    @Autowired ProcurementService procurement;
    @Autowired CompanyPricingService companies;
    @Autowired FinancePersonRepository people;
    @Autowired EntityManager em;
    @SpyBean FinanceTime time;
    private final UUID actor=UUID.randomUUID();
    private final LocalDate day=LocalDate.of(2026,7,15);
    @BeforeEach void clock(){doReturn(day).when(time).today();doReturn(day.atTime(12,0)).when(time).now();for(var person:people.findAll())person.setConfirmed(true);}
    private UUID person(String role){return people.findByEnabledTrueOrderByCreatedAtAsc().stream().filter(p->role.equals(p.getBaseRole())).findFirst().orElseThrow().getId();}
    private FinanceTrade trade(String income,String cost,UUID referrer){
        Product p=new Product();p.setTitle("Settlement");p.setCategoryId(UUID.randomUUID());p.setBasePrice(new BigDecimal(income));em.persist(p);
        var company=companies.saveCompany(null,new CompanyPricingService.CompanyInput("S-"+UUID.randomUUID(),true,""),actor);
        var purchase=procurement.createPurchase(new ProcurementService.PurchaseInput("Vendor",day,"SUPPLEMENT",List.of(new ProcurementService.LineInput(p.getId(),null,1)),UUID.randomUUID().toString(),""),actor);
        procurement.receive(purchase.purchase().getId(),new ProcurementService.ReceiveInput(purchase.lines().getFirst().getId(),1,new BigDecimal(cost),UUID.randomUUID().toString(),null,day),actor);
        var view=trades.createOffline(new FinanceTradeService.OfflineInput(company.getId(),UUID.randomUUID().toString(),day,List.of(new FinanceTradeService.ItemInput(p.getId(),null,1,new BigDecimal(income),"")),referrer,"",UUID.randomUUID().toString()),actor);
        view=trades.allocateCosts(view.trade().getId(),new FinanceTradeService.CostInput(view.items().getFirst().key(),List.of(),view.trade().getVersion()),actor);
        view=trades.recordReceipt(view.trade().getId(),new FinanceTradeService.ReceiptInput(new BigDecimal(income),day,true,"",UUID.randomUUID().toString(),view.trade().getVersion()),actor);
        return trades.confirm(view.trade().getId(),view.trade().getVersion(),actor).trade();
    }
    private FinanceSettlementService.Document settle(FinanceTrade trade){var request=new FinanceSettlementService.ProfitInput(List.of(trade.getId()),UUID.randomUUID().toString());var preview=settlements.previewProfit(request);return settlements.confirmProfit(new FinanceSettlementService.ProfitInput(request.tradeIds(),request.idempotencyKey(),preview.previewToken()),actor);}
    private FinanceSettlementService.QuarterInput quarterInput(String key){return new FinanceSettlementService.QuarterInput(key,UUID.randomUUID().toString(),settlements.previewQuarter(key).previewToken());}
    private void equal(String value,BigDecimal actual){assertEquals(0,new BigDecimal(value).compareTo(actual));}
    @Test void confirmedTradesAccrueOperatingReserveOnceAndPeriodReportSurvivesSettlement(){
        var row=trade("100.00","0.00",null);equal("10.00",settlements.operatingBalance());
        settlements.recordCash(new FinanceSettlementService.CashInput("EXPENSE",null,null,new BigDecimal("30.00"),day,"EXP-ONE","risk",UUID.randomUUID().toString(),null),actor);
        equal("-20.00",settlements.operatingBalance());settle(row);equal("-20.00",settlements.operatingBalance());
        var report=settlements.reserveReport(day,day);equal("0",report.get("opening"));equal("10",report.get("accrual"));equal("30",report.get("expenses"));equal("-20",report.get("closing"));
    }
    @Test void realExpenseReferenceDeduplicatesAcrossNewFormsAndPersonalCashRemainsVisible(){
        var original=settle(trade("100.00","0.00",null));UUID tech=person("TECH");
        var a=settlements.recordCash(new FinanceSettlementService.CashInput("EXPENSE",null,null,new BigDecimal("3.00"),day,"BANK-EXP","risk",UUID.randomUUID().toString(),null),actor);
        var b=settlements.recordCash(new FinanceSettlementService.CashInput("EXPENSE",null,null,new BigDecimal("3.00"),day,"BANK-EXP","risk",UUID.randomUUID().toString(),null),actor);assertEquals(a.getId(),b.getId());
        settlements.recordCash(new FinanceSettlementService.CashInput("PAYOUT",tech,original.document().getId(),new BigDecimal("12.00"),day,"BANK-PAY","partial",UUID.randomUUID().toString(),null),actor);
        assertEquals("BANK-PAY",settlements.personalCash(tech).getFirst().getReference());equal("7.00",settlements.operatingBalance());
    }
    @Test void actualPeopleMustBeConfirmedAndEffectiveReferralFollowsLaterCorrections(){
        var row=trade("100.00","0.00",null);UUID tech=person("TECH");people.findById(tech).orElseThrow().setConfirmed(false);
        assertThrows(BusinessException.class,()->settle(row));people.findById(tech).orElseThrow().setConfirmed(true);settle(row);
        settlements.adjust(new FinanceSettlementService.AdjustInput(row.getId(),BigDecimal.ZERO,BigDecimal.ZERO,tech,true,"confirmed referral",UUID.randomUUID().toString()),actor);
        var view=trades.detail(row.getId());assertNull(view.trade().getReferrerId());assertEquals(tech,view.effectiveReferrerId());
    }
    @Test void noReferrerAccruesSeparateQuarterPoolAndProfitCannotSettleTwice(){
        var trade=trade("100.00","0.00",null);settle(trade);
        equal("10.00",settlements.operatingBalance());equal("13.50",settlements.dividendBalance());equal("36.00",settlements.personBalance(person("TECH")));
        assertThrows(BusinessException.class,()->settle(trade));
    }
    @Test void quarterlyDefaultSharesOnlyReferralReserveEqually(){
        settle(trade("100.00","0.00",null));doReturn(LocalDate.of(2026,10,1)).when(time).today();
        var preview=settlements.previewQuarter("2026-Q3");equal("13.50",preview.amount());
        equal("4.50",preview.people().get(person("TECH")));equal("4.50",preview.people().get(person("BOSS")));
        settlements.confirmQuarter(quarterInput("2026-Q3"),actor);
        equal("0",settlements.dividendBalance());equal("10",settlements.operatingBalance());equal("40.50",settlements.personBalance(person("TECH")));
        assertThrows(BusinessException.class,()->settlements.confirmQuarter(quarterInput("2026-Q3"),actor));
    }
    @Test void quarterWeightsCanChangeWithoutRewritingAlreadyConfirmedDividend(){
        settle(trade("100.00","0.00",null));doReturn(LocalDate.of(2026,10,1)).when(time).today();
        var tech=people.findById(person("TECH")).orElseThrow();
        settlements.savePerson(tech.getId(),new FinanceSettlementService.PersonInput(tech.getName(),tech.getBaseRole(),true,new BigDecimal("2"),tech.getVersion()),actor);
        var preview=settlements.previewQuarter("2026-Q3");equal("6.75",preview.people().get(tech.getId()));
        var doc=settlements.confirmQuarter(quarterInput("2026-Q3"),actor);
        String frozen=doc.document().getSnapshotJson();
        tech=people.findById(tech.getId()).orElseThrow();settlements.savePerson(tech.getId(),new FinanceSettlementService.PersonInput(tech.getName(),tech.getBaseRole(),true,new BigDecimal("8"),tech.getVersion()),actor);
        assertEquals(frozen,settlements.detail(doc.document().getId()).document().getSnapshotJson());
    }
    @Test void laterReferralOnFiveCentProfitNeverAddsAnExtraCent(){
        var trade=trade("0.05","0.00",null);settle(trade);UUID tech=person("TECH");equal("0.02",settlements.personBalance(tech));
        settlements.adjust(new FinanceSettlementService.AdjustInput(trade.getId(),BigDecimal.ZERO,BigDecimal.ZERO,tech,true,"later referral",UUID.randomUUID().toString()),actor);
        equal("0.02",settlements.personBalance(tech));equal("0",settlements.dividendBalance());
    }
    @Test void paidProfitRefundCreatesPersonalDebtAndRecoveryIsNotSalesIncome(){
        var trade=trade("100.00","0.00",person("TECH"));var doc=settle(trade);UUID tech=person("TECH");
        settlements.recordCash(new FinanceSettlementService.CashInput("PAYOUT",tech,doc.document().getId(),new BigDecimal("49.50"),day,"bank-1","",UUID.randomUUID().toString(),null),actor);
        equal("0",settlements.personBalance(tech));
        settlements.adjust(new FinanceSettlementService.AdjustInput(trade.getId(),new BigDecimal("-100.00"),BigDecimal.ZERO,null,false,"refund",UUID.randomUUID().toString()),actor);
        equal("-49.50",settlements.personBalance(tech));
        settlements.recordCash(new FinanceSettlementService.CashInput("RECOVERY",tech,doc.document().getId(),new BigDecimal("49.50"),day,"bank-2","returned",UUID.randomUUID().toString(),null),actor);
        equal("0",settlements.personBalance(tech));equal("0",settlements.operatingBalance());
        assertEquals(new BigDecimal("100.00"),trades.detail(trade.getId()).trade().getReceivedAmount());
        var report=trades.list(day,day,null,null,null,null,1,20);
        equal("0",(BigDecimal)((Map<?,?>)report.get("totals")).get("confirmed_profit"));
    }
    @Test void operatingExpenseNeverConsumesQuarterReserveAndRetriesDoNotDoubleCount(){
        settle(trade("100.00","0.00",null));String key=UUID.randomUUID().toString();
        var input=new FinanceSettlementService.CashInput("EXPENSE",null,null,new BigDecimal("30.00"),day,"expense-1","risk compensation",key,null);
        settlements.recordCash(input,actor);settlements.recordCash(input,actor);
        equal("-20",settlements.operatingBalance());equal("13.50",settlements.dividendBalance());equal("36",settlements.personBalance(person("TECH")));
    }
    @Test void refundAfterQuarterDividendUsesOriginalQuarterPeopleAndWeights(){
        var trade=trade("100.00","0.00",null);settle(trade);doReturn(LocalDate.of(2026,10,1)).when(time).today();
        var quarter=settlements.confirmQuarter(quarterInput("2026-Q3"),actor);
        UUID tech=person("TECH");settlements.recordCash(new FinanceSettlementService.CashInput("PAYOUT",tech,quarter.document().getId(),new BigDecimal("40.50"),LocalDate.of(2026,10,1),"paid-all","",UUID.randomUUID().toString(),null),actor);
        settlements.adjust(new FinanceSettlementService.AdjustInput(trade.getId(),new BigDecimal("-100.00"),BigDecimal.ZERO,null,false,"refund after dividend",UUID.randomUUID().toString()),actor);
        equal("-40.50",settlements.personBalance(tech));equal("0",settlements.dividendBalance());
    }
    @Test void quarterCannotBeDistributedBeforeItEnds(){
        settle(trade("100.00","0.00",null));assertThrows(BusinessException.class,()->settlements.confirmQuarter(quarterInput("2026-Q3"),actor));
    }
    @Test void staleQuarterPreviewCannotConfirmDifferentWeights(){
        settle(trade("100.00","0.00",null));doReturn(LocalDate.of(2026,10,1)).when(time).today();
        var preview=settlements.previewQuarter("2026-Q3");var tech=people.findById(person("TECH")).orElseThrow();
        settlements.savePerson(tech.getId(),new FinanceSettlementService.PersonInput(tech.getName(),tech.getBaseRole(),true,new BigDecimal("7"),tech.getVersion()),actor);
        assertThrows(BusinessException.class,()->settlements.confirmQuarter(new FinanceSettlementService.QuarterInput("2026-Q3",UUID.randomUUID().toString(),preview.previewToken()),actor));
    }
}

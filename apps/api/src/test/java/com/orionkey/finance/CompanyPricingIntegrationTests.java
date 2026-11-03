package com.orionkey.finance;

import com.orionkey.entity.*;
import com.orionkey.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class CompanyPricingIntegrationTests {
    @Autowired CompanyPricingService service;
    @Autowired EntityManager em;
    private final UUID actor=UUID.randomUUID();
    private Product product(){Product p=new Product();p.setTitle("Company product");p.setCategoryId(UUID.randomUUID());p.setBasePrice(new BigDecimal("135.00"));em.persist(p);return p;}
    private User user(){User u=new User();u.setUsername("enterprise-"+UUID.randomUUID());u.setEmail(UUID.randomUUID()+"@example.test");u.setPasswordHash("TEST-NOT-A-PASSWORD");em.persist(u);return u;}
    @Test void differentCompaniesUseTheirOwnAgreementsWhileGuestsKeepRetailPrice(){
        var p=product();var a=user();var b=user();
        var ca=service.saveCompany(null,new CompanyPricingService.CompanyInput("A-"+UUID.randomUUID(),true,""),actor);
        var cb=service.saveCompany(null,new CompanyPricingService.CompanyInput("B-"+UUID.randomUUID(),true,""),actor);
        service.bindUser(ca.getId(),a.getId(),actor);service.bindUser(cb.getId(),b.getId(),actor);
        service.saveQuote(ca.getId(),p.getId(),null,new BigDecimal("125.00"),actor);
        service.saveQuote(cb.getId(),p.getId(),null,new BigDecimal("118.00"),actor);
        assertEquals(new BigDecimal("125.00"),service.resolve(a.getId(),p,null,1).unitPrice());
        assertEquals(new BigDecimal("118.00"),service.resolve(b.getId(),p,null,1).unitPrice());
        assertEquals(new BigDecimal("135.00"),service.resolve(null,p,null,1).unitPrice());
    }
    @Test void quoteChangesCreateNewVersionWithoutMutatingOldQuote(){
        var p=product();var u=user();var c=service.saveCompany(null,new CompanyPricingService.CompanyInput("C-"+UUID.randomUUID(),true,""),actor);service.bindUser(c.getId(),u.getId(),actor);
        var first=service.saveQuote(c.getId(),p.getId(),null,new BigDecimal("120.00"),actor);
        var second=service.saveQuote(c.getId(),p.getId(),null,new BigDecimal("121.00"),actor);
        assertNotEquals(first.getId(),second.getId());assertEquals(new BigDecimal("120.00"),first.getUnitPrice());
        assertEquals(second.getId(),service.resolve(u.getId(),p,null,1).quoteId());
        assertThrows(BusinessException.class,()->service.assertExpectedTotal("120.00",new BigDecimal("121.00")));
    }
    @Test void exactSkuIsRequiredAndFallbackUsesExistingWholesaleRules(){
        var p=product();p.setSpecEnabled(true);p.setWholesaleEnabled(true);var u=user();
        ProductSpec monthly=new ProductSpec();monthly.setProductId(p.getId());monthly.setName("Monthly");monthly.setPrice(new BigDecimal("149.00"));em.persist(monthly);
        ProductSpec annual=new ProductSpec();annual.setProductId(p.getId());annual.setName("Annual");annual.setPrice(new BigDecimal("1499.00"));em.persist(annual);
        var c=service.saveCompany(null,new CompanyPricingService.CompanyInput("D-"+UUID.randomUUID(),true,""),actor);service.bindUser(c.getId(),u.getId(),actor);
        service.saveQuote(c.getId(),p.getId(),monthly.getId(),new BigDecimal("130.00"),actor);
        assertEquals(new BigDecimal("130.00"),service.resolve(u.getId(),p,monthly.getId(),1).unitPrice());
        assertEquals(new BigDecimal("1499.00"),service.resolve(u.getId(),p,annual.getId(),1).unitPrice());
        assertThrows(BusinessException.class,()->service.saveQuote(c.getId(),p.getId(),null,new BigDecimal("1.00"),actor));
    }
    @Test void stoppedCompanyCannotSilentlyCheckoutAtAnotherPrice(){
        var p=product();var u=user();var c=service.saveCompany(null,new CompanyPricingService.CompanyInput("E-"+UUID.randomUUID(),true,""),actor);service.bindUser(c.getId(),u.getId(),actor);
        service.saveCompany(c.getId(),new CompanyPricingService.CompanyInput(c.getName(),false,"stopped"),actor);
        assertThrows(BusinessException.class,()->service.resolve(u.getId(),p,null,1));
    }
    @Test void onlineQuotesAndTotalsStayWithinTheExistingPaymentMoneyRange(){
        var p=product();var c=service.saveCompany(null,new CompanyPricingService.CompanyInput("Bounds-"+UUID.randomUUID(),true,""),actor);
        assertThrows(BusinessException.class,()->service.saveQuote(c.getId(),p.getId(),null,BigDecimal.ZERO,actor));
        assertThrows(BusinessException.class,()->service.saveQuote(c.getId(),p.getId(),null,new BigDecimal("100000000.00"),actor));
        assertThrows(BusinessException.class,()->service.assertExpectedTotal(null,new BigDecimal("100000000.00")));
    }
}

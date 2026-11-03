package com.orionkey.finance;

import com.orionkey.context.RequestContext;
import com.orionkey.entity.*;
import com.orionkey.exception.BusinessException;
import com.orionkey.service.*;
import com.orionkey.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest @Transactional
class CompanyCheckoutIntegrationTests {
    @Autowired CompanyPricingService pricing;
    @Autowired ProductService products;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItems;
    @Autowired EntityManager em;
    @MockBean com.orionkey.service.impl.PaymentServiceImpl payments;
    private Product product;private User user;private UUID companyId;
    @BeforeEach void fixtures(){
        product=new Product();product.setTitle("Checkout");product.setCategoryId(UUID.randomUUID());product.setBasePrice(new BigDecimal("135.00"));em.persist(product);
        user=new User();user.setUsername("buyer-"+UUID.randomUUID());user.setEmail(UUID.randomUUID()+"@example.test");user.setPasswordHash("TEST");em.persist(user);
        var company=pricing.saveCompany(null,new CompanyPricingService.CompanyInput("Checkout-"+UUID.randomUUID(),true,""),user.getId());companyId=company.getId();
        pricing.bindUser(companyId,user.getId(),user.getId());pricing.saveQuote(companyId,product.getId(),null,new BigDecimal("120.00"),user.getId());
        CardKey key=new CardKey();key.setProductId(product.getId());key.setContent("TEST-"+UUID.randomUUID());em.persist(key);
        PaymentChannel channel=new PaymentChannel();channel.setChannelCode("test-"+UUID.randomUUID());channel.setChannelName("Test");channel.setEnabled(true);channel.setConfigData("{}");em.persist(channel);channelCode=channel.getChannelCode();
        when(payments.createPayment(any(),anyString(),any(),any())).thenReturn(Map.of("test",true));
        RequestContext.set(new RequestContext.UserInfo(user.getId(),user.getUsername(),"USER"));
    }
    private String channelCode;
    @AfterEach void clear(){RequestContext.clear();}
    private Map<String,Object> request(String expected){return Map.of("product_id",product.getId().toString(),"quantity",1,"email",user.getEmail(),"payment_method",channelCode,"idempotency_key",UUID.randomUUID().toString(),"expected_total",expected);}
    @Test void productDisplayUsesPrivateAgreementButPublicDisplayDoesNot(){
        assertEquals(new BigDecimal("120.00"),products.getProductDetail(product.getId()).get("base_price"));
        RequestContext.clear();assertEquals(new BigDecimal("135.00"),products.getProductDetail(product.getId()).get("base_price"));
    }
    @Test void directOrderFreezesAgreementPriceAndCompany(){
        var result=orders.createDirectOrder(request("120.00"),user.getId(),"127.0.0.1",null);
        UUID id=(UUID)((Map<?,?>)result.get("order")).get("id");
        var order=orderRepository.findById(id).orElseThrow();
        assertEquals(new BigDecimal("120.00"),order.getActualAmount());assertEquals(companyId,order.getCompanyId());
        assertNotNull(orderItems.findByOrderId(id).getFirst().getCompanyQuoteId());
        pricing.saveQuote(companyId,product.getId(),null,new BigDecimal("121.00"),user.getId());
        assertEquals(new BigDecimal("120.00"),orderItems.findByOrderId(id).getFirst().getUnitPrice());
    }
    @Test void staleDisplayedPriceCannotSilentlyCreateChangedOrder(){
        assertThrows(BusinessException.class,()->orders.createDirectOrder(request("119.00"),user.getId(),"127.0.0.1",null));
    }
    @Test void cartAndCartCheckoutUseTheSameAgreement(){
        carts.addItem(user.getId(),null,Map.of("product_id",product.getId().toString(),"quantity",1));
        var cart=carts.getCart(user.getId(),null);
        var item=(Map<?,?>)((List<?>)cart.get("items")).getFirst();
        assertEquals(new BigDecimal("120.00"),item.get("unit_price"));
        var result=orders.createCartOrder(Map.of("email",user.getEmail(),"payment_method",channelCode,"idempotency_key",UUID.randomUUID().toString(),"expected_total","120.00"),user.getId(),"127.0.0.1",null);
        UUID id=(UUID)((Map<?,?>)result.get("order")).get("id");
        assertEquals(new BigDecimal("120.00"),orderRepository.findById(id).orElseThrow().getActualAmount());
    }
}

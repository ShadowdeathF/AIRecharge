package com.orionkey.finance;
import com.orionkey.entity.*;
import com.orionkey.constant.*;
import com.orionkey.service.DeliverService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class DeliveryStockIntegrationTests {
    @Autowired DeliverService delivery;
    @Autowired EntityManager em;
    private record Fixture(Order order,List<CardKey> keys){}
    private Fixture fixture(int stock){
        Product p=new Product();p.setTitle("Repeated SKU");p.setCategoryId(UUID.randomUUID());p.setBasePrice(new BigDecimal("100.00"));em.persist(p);
        Order order=new Order();order.setStatus(OrderStatus.PAID);order.setOrderType(OrderType.CART);order.setPaidAt(LocalDateTime.now());order.setActualAmount(new BigDecimal("200.00"));em.persist(order);
        for(int i=0;i<2;i++){OrderItem item=new OrderItem();item.setOrderId(order.getId());item.setProductId(p.getId());item.setProductTitle(p.getTitle());item.setQuantity(1);item.setUnitPrice(new BigDecimal("100.00"));item.setSubtotal(new BigDecimal("100.00"));em.persist(item);}
        List<CardKey> keys=new ArrayList<>();for(int i=0;i<stock;i++){CardKey key=new CardKey();key.setProductId(p.getId());key.setContent("TEST-STOCK-"+UUID.randomUUID());em.persist(key);keys.add(key);}em.flush();return new Fixture(order,keys);
    }
    @Test void shortageLeavesAllKeysAvailableInsteadOfPartiallyConsumingPurchaseStock(){
        var f=fixture(1);delivery.deliverOrders(Map.of("order_ids",List.of(f.order().getId().toString())));
        assertEquals(OrderStatus.PAID,f.order().getStatus());assertEquals(CardKeyStatus.AVAILABLE,f.keys().getFirst().getStatus());
    }
    @Test void repeatedSkuLinesNeverReceiveTheSameCardKey(){
        var f=fixture(2);delivery.deliverOrders(Map.of("order_ids",List.of(f.order().getId().toString())));
        assertEquals(OrderStatus.DELIVERED,f.order().getStatus());
        assertTrue(f.keys().stream().allMatch(k->k.getStatus()==CardKeyStatus.SOLD));assertNotEquals(f.keys().get(0).getOrderItemId(),f.keys().get(1).getOrderItemId());
    }
}

package com.orionkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.CardKeyStatus;
import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.CardKey;
import com.orionkey.entity.Order;
import com.orionkey.entity.Product;
import com.orionkey.finance.FinanceOrderEvent;
import com.orionkey.repository.OrderRepository;
import com.orionkey.utils.JwtUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "payment.mock-enabled=true")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class MockPaymentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired OrderRepository orders;
    @Autowired JwtUtils jwt;
    @Autowired ApplicationEvents events;

    private Product product;

    @BeforeEach
    void prepareProduct() {
        product = new Product();
        product.setTitle("Mock payment fixture");
        product.setCategoryId(UUID.randomUUID());
        product.setBasePrice(new BigDecimal("12.34"));
        em.persist(product);
        CardKey card = new CardKey();
        card.setProductId(product.getId());
        card.setContent("TEST-ONLY-" + UUID.randomUUID());
        em.persist(card);
    }

    @Test
    void enabledChannelCreatesGuestOrderThenPaysAndDeliversOnce() throws Exception {
        mvc.perform(get("/payment-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channel_code == 'mock')].is_enabled").value(true));

        var created = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "product_id", product.getId().toString(), "quantity", 1,
                                "email", "mock@example.test", "payment_method", "mock",
                                "idempotency_key", UUID.randomUUID().toString(), "expected_total", "12.34"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Session-Token"))
                .andExpect(jsonPath("$.data.order.status").value("PENDING"))
                .andReturn().getResponse();
        String session = created.getHeader("X-Session-Token");
        UUID id = UUID.fromString(json.readTree(created.getContentAsString()).at("/data/order/id").asText());
        assertEquals(session, orders.findById(id).orElseThrow().getSessionToken());
        mvc.perform(get("/orders/" + id + "/status"))
                .andExpect(jsonPath("$.data.mock_payment").value(true));

        pay(id, session);
        LocalDateTime paidAt = orders.findById(id).orElseThrow().getPaidAt();
        pay(id, session);
        assertEquals(paidAt, orders.findById(id).orElseThrow().getPaidAt());
        assertEquals(1, events.stream(FinanceOrderEvent.class).filter(e -> e.orderId().equals(id)).count());

        String delivery = json.writeValueAsString(Map.of("order_ids", new String[]{id.toString()}));
        mvc.perform(post("/orders/deliver").contentType(MediaType.APPLICATION_JSON).content(delivery))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("DELIVERED"));
        mvc.perform(post("/orders/" + id + "/mock-pay").header("X-Session-Token", session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DELIVERED"));
        assertEquals(1L, em.createQuery("select count(k) from CardKey k where k.orderId = :id and k.status = :status", Long.class)
                .setParameter("id", id).setParameter("status", CardKeyStatus.SOLD).getSingleResult());
    }

    @Test
    void missingOrWrongGuestSessionCannotPay() throws Exception {
        Order order = pendingOrder();
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay").header("X-Session-Token", "wrong"))
                .andExpect(status().isForbidden());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void accountOrderRequiresItsOwnAccountEvenWhenGuestTokenMatches() throws Exception {
        Order order = pendingOrder();
        UUID owner = UUID.randomUUID();
        order.setUserId(owner);
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("X-Session-Token", order.getSessionToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("Authorization", "Bearer " + jwt.generateToken(UUID.randomUUID(), "other", "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("Authorization", "Bearer " + jwt.generateToken(owner, "owner", "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void realChannelOrderCannotBeMarkedPaidThroughMockEndpoint() throws Exception {
        Order order = pendingOrder();
        order.setPaymentMethod("alipay");
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("X-Session-Token", order.getSessionToken()))
                .andExpect(status().isBadRequest());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void overdueOrExpiredOrdersCannotBePaid() throws Exception {
        Order order = pendingOrder();
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("X-Session-Token", order.getSessionToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40002));
        assertNull(order.getPaidAt());
        order.setStatus(OrderStatus.EXPIRED);
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("X-Session-Token", order.getSessionToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40002));
    }

    private void pay(UUID id, String session) throws Exception {
        mvc.perform(post("/orders/" + id + "/mock-pay").header("X-Session-Token", session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setOrderType(OrderType.DIRECT);
        order.setPaymentMethod("mock");
        order.setSessionToken(UUID.randomUUID().toString());
        order.setTotalAmount(new BigDecimal("12.34"));
        order.setActualAmount(new BigDecimal("12.34"));
        order.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        em.persist(order);
        return order;
    }
}

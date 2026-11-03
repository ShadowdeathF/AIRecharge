package com.orionkey;

import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.Order;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MockPaymentDisabledTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    void defaultConfigurationHidesMockChannelAndRejectsPayment() throws Exception {
        mvc.perform(get("/payment-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.channel_code == 'mock')]").isEmpty());
        Order order = new Order();
        order.setOrderType(OrderType.DIRECT);
        order.setPaymentMethod("mock");
        order.setSessionToken("disabled-test-session");
        order.setActualAmount(BigDecimal.ONE);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        em.persist(order);
        mvc.perform(get("/orders/" + order.getId() + "/status"))
                .andExpect(jsonPath("$.data.mock_payment").value(false));
        mvc.perform(post("/orders/" + order.getId() + "/mock-pay")
                        .header("X-Session-Token", "disabled-test-session"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(50001));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void disabledMockCannotCreateAnOrder() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"product_id":"00000000-0000-0000-0000-000000000001","quantity":1,
                                 "email":"mock@example.test","payment_method":"mock"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(50001));
    }
}

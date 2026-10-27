package com.orionkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.controller.OrderController;
import com.orionkey.controller.PaymentChannelController;
import com.orionkey.controller.PaymentWebhookController;
import com.orionkey.controller.UserController;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.PaymentChannelRepository;
import com.orionkey.service.*;
import com.orionkey.service.impl.AdminPaymentChannelServiceImpl;
import com.orionkey.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RemovedFeaturesTests {
    @Test
    void oldStoredUsdtChannelsCannotBeUsedForPayment() {
        var repository = mock(PaymentChannelRepository.class);
        var channel = channel("usdt_trc20", "usdt");
        when(repository.findByChannelCodeAndIsDeleted("usdt_trc20", 0)).thenReturn(Optional.of(channel));
        var service = new PaymentServiceImpl(repository, null, null, null, new ObjectMapper());
        assertThrows(BusinessException.class, () -> service.createPayment(UUID.randomUUID(), "usdt_trc20", BigDecimal.ONE, "pc"));
    }

    @Test
    void channelsExposeFiatPaymentButExcludeOldUsdtRecords() throws Exception {
        var repository = mock(PaymentChannelRepository.class);
        when(repository.findByEnabledAndIsDeletedOrderBySortOrderAsc(true, 0))
                .thenReturn(List.of(channel("alipay", "epay"), channel("usdt_trc20", "usdt")));
        MockMvcBuilders.standaloneSetup(new PaymentChannelController(repository)).build()
                .perform(get("/payment-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].channel_code").value("alipay"));
    }

    @Test
    void administratorsCannotRecreateRemovedChannel() {
        var repository = mock(PaymentChannelRepository.class);
        var service = new AdminPaymentChannelServiceImpl(repository, new ObjectMapper());
        assertThrows(BusinessException.class, () -> service.createChannel(Map.of("channel_code", "custom", "provider_type", "usdt")));
        assertThrows(BusinessException.class, () -> service.createChannel(Map.of("channel_code", "usdt_bep20", "provider_type", "epay")));
        verifyNoInteractions(repository);
    }

    @Test
    void removedEndpointsHaveNoHandlers() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                new UserController(mock(UserService.class)),
                new PaymentWebhookController(mock(WebhookService.class)),
                new OrderController(mock(OrderService.class), mock(DeliverService.class), mock(PaymentService.class)))
                .build();
        mvc.perform(get("/user/points")).andExpect(status().isNotFound());
        mvc.perform(post("/payments/webhook/usdt")).andExpect(status().isNotFound());
        mvc.perform(post("/orders/" + UUID.randomUUID() + "/txid-verify")).andExpect(status().isNotFound());
    }

    private PaymentChannel channel(String code, String provider) {
        var channel = new PaymentChannel();
        channel.setChannelCode(code);
        channel.setProviderType(provider);
        channel.setEnabled(true);
        return channel;
    }
}

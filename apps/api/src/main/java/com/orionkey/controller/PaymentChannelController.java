package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.repository.PaymentChannelRepository;
import com.orionkey.service.MockPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payment-channels")
@RequiredArgsConstructor
public class PaymentChannelController {

    private final PaymentChannelRepository paymentChannelRepository;
    private final MockPaymentService mockPayments;

    @GetMapping
    public ApiResponse<?> listEnabledChannels() {
        List<Map<String, Object>> channels = new ArrayList<>(paymentChannelRepository
                .findByEnabledAndIsDeletedOrderBySortOrderAsc(true, 0)
                .stream()
                .filter(c -> !"usdt".equalsIgnoreCase(c.getProviderType()) && !c.getChannelCode().toLowerCase().startsWith("usdt"))
                .filter(c -> !MockPaymentService.CHANNEL_CODE.equalsIgnoreCase(c.getChannelCode()))
                .map(this::toPublicMap)
                .toList());
        if (mockPayments.isEnabled()) {
            channels.add(Map.of("id", "mock", "channel_code", MockPaymentService.CHANNEL_CODE,
                    "channel_name", "模拟支付（测试）", "provider_type", "mock",
                    "is_enabled", true, "sort_order", Integer.MAX_VALUE, "created_at", ""));
        }
        return ApiResponse.success(channels);
    }

    private Map<String, Object> toPublicMap(PaymentChannel c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("channel_code", c.getChannelCode());
        map.put("channel_name", c.getChannelName());
        map.put("is_enabled", c.isEnabled());
        map.put("sort_order", c.getSortOrder());
        map.put("created_at", c.getCreatedAt());
        return map;
    }
}

package com.orionkey.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.exception.BusinessException;
import com.orionkey.entity.Order;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.entity.WebhookEvent;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.PaymentChannelRepository;
import com.orionkey.repository.WebhookEventRepository;
import com.orionkey.service.EpayService;
import com.orionkey.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final EpayService epayService;
    private final ObjectMapper objectMapper;
    private final PaymentServiceImpl paymentService;

    @Override
    @Transactional
    public String processEpayCallback(Map<String, String> params) {
        String tradeNo = params.get("trade_no");
        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String money = params.get("money");
        String sign = params.get("sign");

        log.info("Epay callback: out_trade_no={}, trade_status={}, money={}", outTradeNo, tradeStatus, money);

        // Use trade_no as event ID for idempotency
        String eventId = "epay_" + (tradeNo != null ? tradeNo : UUID.randomUUID().toString());
        Optional<WebhookEvent> existingEvent = webhookEventRepository.findByEventId(eventId);
        if (existingEvent.isPresent()) {
            log.info("Epay callback already processed: {}", eventId);
            return "SUCCESS";
        }

        // Step 1: Parse order ID
        UUID orderId;
        try {
            orderId = UUID.fromString(outTradeNo);
        } catch (IllegalArgumentException e) {
            log.error("Epay callback invalid out_trade_no: {}", outTradeNo);
            return "FAIL";
        }

        // Step 2: Resolve merchant key from order's channel config
        String merchantKey = resolveMerchantKey(orderId);

        // Step 3: Verify signature
        // F3: 签名失败不写入幂等表 — 否则攻击者可伪造回调占用 eventId，阻塞后续真实回调
        if (!epayService.verifySign(merchantKey, params, sign)) {
            log.error("Epay callback signature verification failed: out_trade_no={}, remote sign={}", outTradeNo, sign);
            return "FAIL";
        }

        // Step 4: Check trade status（非成功状态不写入幂等表，避免阻塞后续成功回调）
        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            log.info("Epay callback non-success status: {}, skipping (not saved to idempotency table)", tradeStatus);
            return "SUCCESS";
        }

        // Step 5: Process payment
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setChannelCode("epay");
        event.setOrderId(orderId);
        event.setPayload(params.toString());

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            event.setProcessResult("ORDER_NOT_FOUND");
            log.warn("Epay callback order not found: {}", orderId);
            webhookEventRepository.save(event);
            return "SUCCESS";
        }

        // Step 6: Verify amount matches (money 必须存在且与订单金额一致)
        if (money == null || money.isBlank()) {
            log.error("Epay callback missing money parameter: out_trade_no={}", outTradeNo);
            event.setProcessResult("MISSING_AMOUNT");
            webhookEventRepository.save(event);
            return "FAIL";
        }
        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(money);
        } catch (NumberFormatException e) {
            log.error("Epay callback invalid money format: {}, out_trade_no={}", money, outTradeNo);
            event.setProcessResult("INVALID_AMOUNT_FORMAT");
            webhookEventRepository.save(event);
            return "FAIL";
        }
        if (order.getActualAmount().compareTo(callbackAmount) != 0) {
            log.error("Epay callback amount mismatch: order={}, callback={}", order.getActualAmount(), callbackAmount);
            event.setProcessResult("AMOUNT_MISMATCH");
            webhookEventRepository.save(event);
            return "FAIL";
        }

        // Step 7: 服务端主动查询网关订单状态（防止伪造回调）
        EpayService.ChannelConfig channelConfig = resolveChannelConfig(order);
        if (channelConfig != null) {
            EpayService.OrderQueryResult queryResult = epayService.queryOrder(channelConfig, outTradeNo);
            if (queryResult == null) {
                // 网络/网关故障 — 不写入幂等表，返回 FAIL 触发网关重试
                log.warn("Epay callback deferred: server-side order query returned null (network issue?), out_trade_no={}", outTradeNo);
                return "FAIL";
            }
            // 查询 API 的 status 字段格式可能为 "TRADE_SUCCESS" 或 "1"（已支付），兼容两种
            if (!isQueryStatusPaid(queryResult.tradeStatus())) {
                log.error("Epay callback rejected: query status={}, expected TRADE_SUCCESS/1, out_trade_no={}",
                        queryResult.tradeStatus(), outTradeNo);
                event.setProcessResult("QUERY_STATUS_MISMATCH");
                webhookEventRepository.save(event);
                return "FAIL";
            }
            // 校验网关返回的金额与订单金额一致
            if (queryResult.money() != null) {
                try {
                    BigDecimal queryAmount = new BigDecimal(queryResult.money());
                    if (order.getActualAmount().compareTo(queryAmount) != 0) {
                        log.error("Epay callback rejected: query amount={}, order amount={}, out_trade_no={}",
                                queryAmount, order.getActualAmount(), outTradeNo);
                        event.setProcessResult("QUERY_AMOUNT_MISMATCH");
                        webhookEventRepository.save(event);
                        return "FAIL";
                    }
                } catch (NumberFormatException e) {
                    log.warn("Epay order query returned invalid money format: {}", queryResult.money());
                }
            }
            log.info("Epay callback server-side verification passed: out_trade_no={}, queryStatus={}", outTradeNo, queryResult.tradeStatus());
        } else {
            // 渠道配置不完整时降级为仅签名校验（已在 Step 3 通过），打 warn 日志
            log.warn("Epay callback: channel config incomplete, skipping server-side query verification for out_trade_no={}", outTradeNo);
        }

        // Step 8: Idempotent update order status
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);
            event.setProcessResult("SUCCESS");
            log.info("Epay callback: order {} marked as PAID", orderId);
        } else {
            event.setProcessResult("SKIPPED_" + order.getStatus().name());
            log.info("Epay callback: order {} already {}", orderId, order.getStatus());
        }

        webhookEventRepository.save(event);
        return "SUCCESS";
    }



    private void saveWebhookEvent(String eventId, String channelCode, UUID orderId,
                                   String payload, String processResult) {
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setChannelCode(channelCode);
        event.setOrderId(orderId != null ? orderId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
        event.setPayload(payload);
        event.setProcessResult(processResult);
        webhookEventRepository.save(event);
    }

/**
     * 根据订单的 paymentMethod 查找渠道 config_data 中的 merchant key。
     * 所有配置均从数据库读取，缺失则抛出异常。
     */
    private String resolveMerchantKey(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getPaymentMethod() != null) {
            PaymentChannel channel = paymentChannelRepository
                    .findByChannelCodeAndIsDeleted(order.getPaymentMethod(), 0)
                    .orElse(null);
            if (channel != null && channel.getConfigData() != null) {
                try {
                    Map<String, Object> cfg = objectMapper.readValue(
                            channel.getConfigData(), new TypeReference<>() {});
                    Object key = cfg.get("key");
                    if (key != null && !key.toString().isBlank()) {
                        return key.toString();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse channel config for key resolution: {}", e.getMessage());
                }
            }
        }
        log.error("Cannot resolve merchant key for order {}: channel config missing 'key' field", orderId);
        throw new BusinessException(ErrorCode.CHANNEL_UNAVAILABLE,
                "支付渠道配置缺少 key，请在后台「支付渠道管理」中完善配置");
    }

    /**
     * 判断查询 API 返回的 status 是否表示"已支付"。
     * 不同 Epay 网关实现可能返回 "TRADE_SUCCESS"（字符串）或 "1"（数字），兼容两种格式。
     */
    private boolean isQueryStatusPaid(String status) {
        return "TRADE_SUCCESS".equals(status) || "1".equals(status);
    }

    /**
     * 从订单关联的支付渠道解析完整的 ChannelConfig（pid/key/apiUrl/notifyUrl/returnUrl）。
     * 用于 webhook 回调后发起服务端主动查询。配置不完整时返回 null（降级为仅签名校验）。
     */
    private EpayService.ChannelConfig resolveChannelConfig(Order order) {
        if (order.getPaymentMethod() == null) return null;
        PaymentChannel channel = paymentChannelRepository
                .findByChannelCodeAndIsDeleted(order.getPaymentMethod(), 0)
                .orElse(null);
        if (channel == null) return null;
        try {
            return paymentService.buildChannelConfig(channel);
        } catch (Exception e) {
            log.warn("Failed to build ChannelConfig for order query: {}", e.getMessage());
            return null;
        }
    }
}

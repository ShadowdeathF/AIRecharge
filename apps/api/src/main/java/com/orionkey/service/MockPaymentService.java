package com.orionkey.service;

import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.exception.BusinessException;
import com.orionkey.finance.FinanceOrderEvent;
import com.orionkey.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentService {
    public static final String CHANNEL_CODE = "mock";

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    @Value("${payment.mock-enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.CHANNEL_UNAVAILABLE, "模拟支付未开启");
        }
    }

    @Transactional
    public Map<String, Object> complete(UUID orderId, UUID userId, String sessionToken) {
        requireEnabled();
        var order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));
        boolean ownsOrder = order.getUserId() != null
                ? order.getUserId().equals(userId)
                : sessionToken != null && !sessionToken.isBlank() && sessionToken.equals(order.getSessionToken());
        if (!ownsOrder) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单", HttpStatus.FORBIDDEN);
        }
        if (!CHANNEL_CODE.equals(order.getPaymentMethod())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅模拟支付订单可使用此接口");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.ORDER_EXPIRED, "订单已过期，请重新下单");
            }
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orders.save(order);
            events.publishEvent(new FinanceOrderEvent(orderId));
            log.info("Mock payment completed for order {}", orderId);
        } else if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "订单状态不允许模拟支付");
        }
        return Map.of("order_id", orderId, "status", order.getStatus().name(), "mock_payment", true);
    }
}

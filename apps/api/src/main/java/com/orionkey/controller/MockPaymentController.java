package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import com.orionkey.service.MockPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class MockPaymentController {
    private final MockPaymentService mockPayments;

    @PostMapping("/{id}/mock-pay")
    public ApiResponse<?> complete(@PathVariable UUID id,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        return ApiResponse.success(mockPayments.complete(id, RequestContext.getUserId(), sessionToken));
    }
}

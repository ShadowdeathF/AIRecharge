package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workbench")
@RequiredArgsConstructor
public class AdminWorkbenchController {

    private final WorkbenchService workbenchService;

    @GetMapping("/overview")
    public ApiResponse<?> overview(
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "payment_method", required = false) String paymentMethod) {
        return ApiResponse.success(workbenchService.overview(startDate, endDate, paymentMethod));
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders(
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "payment_method", required = false) String paymentMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(value = "page_size", defaultValue = "20") String pageSize) {
        return ApiResponse.success(workbenchService.orders(startDate, endDate, paymentMethod, status, keyword, page, pageSize));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "payment_method", required = false) String paymentMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(value = "page_size", defaultValue = "20") String pageSize,
            @RequestParam(required = false) String ids) {
        byte[] csv = workbenchService.export(startDate, endDate, paymentMethod, status, keyword, page, pageSize, ids);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workbench-orders.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }
}

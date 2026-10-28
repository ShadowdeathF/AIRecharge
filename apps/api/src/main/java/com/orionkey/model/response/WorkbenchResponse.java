package com.orionkey.model.response;

import com.orionkey.constant.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class WorkbenchResponse {

    private WorkbenchResponse() {}

    public record Summary(BigDecimal salesAmount, long orderCount, BigDecimal averageOrder, long undeliveredCount) {}

    public record Comparison(LocalDate startDate, LocalDate endDate, Summary summary) {}

    public record Trend(LocalDate date, BigDecimal salesAmount, long orderCount,
                        BigDecimal previousSalesAmount, long previousOrderCount) {}

    public record LowStockProduct(UUID productId, String title, long availableStock, int threshold) {}

    public record Overview(LocalDate startDate, LocalDate endDate, Summary summary, Comparison comparison,
                           List<Trend> trend, List<LowStockProduct> lowStockProducts, String reconciliationStatus) {}

    public record WorkbenchOrder(UUID id, String paymentReference, LocalDateTime paidAt, LocalDateTime createdAt,
                                 BigDecimal actualAmount, OrderStatus status, String paymentMethod,
                                 String productTitle, long itemCount) {}

    public record Pagination(int page, int pageSize, long total) {}

    public record Totals(BigDecimal salesAmount, long orderCount) {}

    public record Ledger(List<WorkbenchOrder> list, Pagination pagination, Totals totals) {}
}

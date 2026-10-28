package com.orionkey.service.impl;

import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.exception.BusinessException;
import com.orionkey.model.response.WorkbenchResponse.*;
import com.orionkey.repository.WorkbenchQueryRepository;
import com.orionkey.repository.WorkbenchQueryRepository.DailyTotals;
import com.orionkey.repository.WorkbenchQueryRepository.Filter;
import com.orionkey.repository.WorkbenchQueryRepository.ItemSummary;
import com.orionkey.repository.WorkbenchQueryRepository.OrderRow;
import com.orionkey.service.WorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class WorkbenchServiceImpl implements WorkbenchService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int EXPORT_LIMIT = 10000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final WorkbenchQueryRepository repository;

    @Override
    public Overview overview(String startDate, String endDate, String paymentMethod) {
        DateRange range = dateRange(startDate, endDate);
        String channel = channel(paymentMethod);
        LocalDate previousStart = range.start().minusDays(range.days());
        LocalDate previousEnd = range.start().minusDays(1);
        List<DailyTotals> daily = repository.dailyTotals(new Filter(previousStart.atStartOfDay(),
                range.end().plusDays(1).atStartOfDay(), channel, null, null, null));
        Map<LocalDate, DailyTotals> byDate = daily.stream().collect(Collectors.toMap(DailyTotals::date, Function.identity()));
        List<Trend> trend = new ArrayList<>();
        for (int i = 0; i < range.days(); i++) {
            LocalDate date = range.start().plusDays(i);
            DailyTotals current = byDate.getOrDefault(date, zeroDay(date));
            LocalDate previousDate = previousStart.plusDays(i);
            DailyTotals previous = byDate.getOrDefault(previousDate, zeroDay(previousDate));
            trend.add(new Trend(date, current.salesAmount(), current.orderCount(), previous.salesAmount(), previous.orderCount()));
        }
        return new Overview(range.start(), range.end(), summary(daily, range.start(), range.end()),
                new Comparison(previousStart, previousEnd, summary(daily, previousStart, previousEnd)),
                trend, repository.lowStockProducts(), "NOT_IMPORTED");
    }

    @Override
    public Ledger orders(String startDate, String endDate, String paymentMethod, String status,
                         String keyword, String page, String pageSize) {
        Filter filter = filter(startDate, endDate, paymentMethod, status, keyword, null);
        Pagination pagination = pagination(page, pageSize);
        Totals totals = repository.totals(filter);
        List<OrderRow> rows = repository.orders(filter, (pagination.page() - 1) * pagination.pageSize(), pagination.pageSize());
        return new Ledger(withItems(rows), new Pagination(pagination.page(), pagination.pageSize(), totals.orderCount()), totals);
    }

    @Override
    public byte[] export(String startDate, String endDate, String paymentMethod, String status,
                         String keyword, String page, String pageSize, String ids) {
        Filter filter = filter(startDate, endDate, paymentMethod, status, keyword, selectedIds(ids));
        pagination(page, pageSize);
        List<OrderRow> rows = repository.orders(filter, 0, EXPORT_LIMIT + 1);
        if (rows.size() > EXPORT_LIMIT) throw badRequest("导出最多支持 10000 条订单，请缩小日期或筛选范围");
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("order_id,payment_reference,paid_at (Asia/Shanghai),created_at (Asia/Shanghai),actual_amount (CNY),status,payment_method,product_title,item_count\r\n");
        for (WorkbenchOrder row : withItems(rows)) {
            csv.append(csvCell(row.id().toString())).append(',').append(csvCell(row.paymentReference())).append(',')
                    .append(csvCell(CSV_TIME.format(row.paidAt()))).append(',')
                    .append(csvCell(row.createdAt() == null ? "" : CSV_TIME.format(row.createdAt()))).append(',')
                    .append(csvCell(row.actualAmount().setScale(2, RoundingMode.HALF_UP).toPlainString())).append(',')
                    .append(csvCell(row.status().name())).append(',').append(csvCell(row.paymentMethod())).append(',')
                    .append(csvCell(row.productTitle())).append(',').append(csvCell(Long.toString(row.itemCount()))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<WorkbenchOrder> withItems(List<OrderRow> rows) {
        Map<UUID, ItemSummary> items = repository.itemSummaries(rows.stream().map(OrderRow::id).toList());
        return rows.stream().map(row -> {
            ItemSummary item = items.getOrDefault(row.id(), new ItemSummary("", 0));
            return new WorkbenchOrder(row.id(), row.paymentReference(), row.paidAt(), row.createdAt(), row.actualAmount(),
                    row.status(), row.paymentMethod(), item.productTitle(), item.itemCount());
        }).toList();
    }

    private Filter filter(String startDate, String endDate, String paymentMethod, String status, String keyword, List<UUID> ids) {
        DateRange range = dateRange(startDate, endDate);
        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            if (!status.equals("PAID") && !status.equals("DELIVERED")) throw badRequest("status 仅支持 PAID 或 DELIVERED");
            orderStatus = OrderStatus.valueOf(status);
        }
        if (keyword != null && keyword.length() > 100) throw badRequest("keyword 最多 100 个字符");
        String search = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return new Filter(range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay(),
                channel(paymentMethod), orderStatus, search, ids);
    }

    private DateRange dateRange(String startDate, String endDate) {
        LocalDate start = date(startDate, "start_date");
        LocalDate end = date(endDate, "end_date");
        if (start.isAfter(end)) throw badRequest("start_date 不能晚于 end_date");
        if (end.isAfter(LocalDate.now(SHANGHAI))) throw badRequest("end_date 不能晚于上海时区的今天");
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 366) throw badRequest("日期范围最多为 366 天（含首尾日期）");
        if (start.minusDays(days).getYear() < 1) throw badRequest("日期过早，无法计算上一周期");
        return new DateRange(start, end, (int) days);
    }

    private LocalDate date(String value, String name) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw badRequest(name + " 必须为 ISO 日期 yyyy-MM-dd");
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw badRequest(name + " 必须为有效日期");
            return date;
        } catch (DateTimeParseException e) {
            throw badRequest(name + " 必须为有效日期");
        }
    }

    private String channel(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 64 || !value.matches("[a-zA-Z0-9_-]+")) throw badRequest("payment_method 必须为最多 64 个字符的渠道代码");
        return value;
    }

    private Pagination pagination(String page, String pageSize) {
        try {
            int number = Integer.parseInt(page);
            int size = Integer.parseInt(pageSize);
            if (number < 1 || size < 1 || size > 100 || (long) (number - 1) * size > Integer.MAX_VALUE) {
                throw badRequest("page 必须大于 0，page_size 为 1 到 100，且分页偏移不能超出范围");
            }
            return new Pagination(number, size, 0);
        } catch (NumberFormatException e) {
            throw badRequest("page 和 page_size 必须为有效整数");
        }
    }

    private List<UUID> selectedIds(String ids) {
        if (ids == null) return null;
        if (ids.isBlank() || ids.length() > EXPORT_LIMIT * 37 - 1) throw badRequest("ids 必须包含 1 到 10000 个有效订单 ID");
        String[] values = ids.split(",", -1);
        if (values.length > EXPORT_LIMIT) throw badRequest("ids 最多支持 10000 个订单 ID");
        LinkedHashSet<UUID> selected = new LinkedHashSet<>();
        for (String value : values) {
            if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw badRequest("ids 包含无效订单 ID");
            }
            selected.add(UUID.fromString(value));
        }
        return List.copyOf(selected);
    }

    private Summary summary(List<DailyTotals> rows, LocalDate start, LocalDate end) {
        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        long count = 0;
        long undelivered = 0;
        for (DailyTotals row : rows) {
            if (row.date().isBefore(start) || row.date().isAfter(end)) continue;
            amount = amount.add(row.salesAmount());
            count += row.orderCount();
            undelivered += row.undeliveredCount();
        }
        BigDecimal average = count == 0 ? BigDecimal.ZERO.setScale(2) : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new Summary(amount, count, average, undelivered);
    }

    private DailyTotals zeroDay(LocalDate date) {
        return new DailyTotals(date, BigDecimal.ZERO.setScale(2), 0, 0);
    }

    private String csvCell(String value) {
        if (value == null) value = "";
        // Spreadsheet programs can ignore leading whitespace before interpreting formulas.
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private record DateRange(LocalDate start, LocalDate end, int days) {}
}

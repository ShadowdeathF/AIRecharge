package com.orionkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.CardKeyStatus;
import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.CardKey;
import com.orionkey.entity.Order;
import com.orionkey.entity.OrderItem;
import com.orionkey.entity.Product;
import com.orionkey.entity.ProductSpec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class WorkbenchIntegrationTests {

    private static final String START = "2026-02-10";
    private static final String END = "2026-02-12";
    private static final AtomicInteger REQUEST_IP = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void overviewUsesPaidTimeHalfOpenRangeAndActualAmounts() throws Exception {
        order("2026-02-10T00:00:00", "10.10", OrderStatus.PAID, "alipay", "Alpha", 2);
        order("2026-02-12T23:59:59.999999", "20.20", OrderStatus.DELIVERED, "wechat", "Beta", 1);
        order("2026-02-11T12:00:00", null, OrderStatus.PAID, null, "Legacy", 1);
        order("2026-02-09T23:59:59", "5.25", OrderStatus.DELIVERED, "alipay", "Prior", 1);
        order("2026-02-13T00:00:00", "800.00", OrderStatus.PAID, "alipay", "Next", 1);
        order("2026-02-11T12:00:00", "900.00", OrderStatus.PENDING, "alipay", "Pending", 1);
        order(null, "700.00", OrderStatus.PAID, "alipay", "No paid time", 1);

        JsonNode result = data(request("overview"));
        assertEquals(START, result.path("start_date").asText());
        assertEquals(END, result.path("end_date").asText());
        assertAmount("30.30", result.at("/summary/sales_amount"));
        assertEquals(3, result.at("/summary/order_count").asInt());
        assertAmount("10.10", result.at("/summary/average_order"));
        assertEquals(2, result.at("/summary/undelivered_count").asInt());
        assertEquals("2026-02-07", result.at("/comparison/start_date").asText());
        assertEquals("2026-02-09", result.at("/comparison/end_date").asText());
        assertAmount("5.25", result.at("/comparison/summary/sales_amount"));
        assertEquals("NOT_IMPORTED", result.path("reconciliation_status").asText());
    }

    @Test
    void trendsZeroFillAndAlignPreviousEqualLengthDays() throws Exception {
        order("2026-02-10T12:00:00", "0.10", OrderStatus.PAID, "alipay", "One", 1);
        order("2026-02-10T13:00:00", "0.20", OrderStatus.DELIVERED, "alipay", "Two", 1);
        order("2026-02-07T12:00:00", "8.00", OrderStatus.PAID, "alipay", "Previous", 1);
        JsonNode trend = data(request("overview")).path("trend");
        assertEquals(3, trend.size());
        assertEquals(START, trend.get(0).path("date").asText());
        assertAmount("0.30", trend.get(0).path("sales_amount"));
        assertEquals(2, trend.get(0).path("order_count").asInt());
        assertAmount("8.00", trend.get(0).path("previous_sales_amount"));
        assertEquals(1, trend.get(0).path("previous_order_count").asInt());
        assertAmount("0", trend.get(1).path("sales_amount"));
        assertAmount("0", trend.get(1).path("previous_sales_amount"));
        assertEquals(0, trend.get(2).path("order_count").asInt());
    }

    @Test
    void overviewAndLedgerShareChannelFilteredTotals() throws Exception {
        order("2026-02-10T12:00:00", "13.17", OrderStatus.PAID, "alipay", "One", 1);
        order("2026-02-11T12:00:00", "24.20", OrderStatus.DELIVERED, "wechat", "Two", 1);
        order("2026-02-08T12:00:00", "7.00", OrderStatus.PAID, "wechat", "Prior", 1);
        JsonNode overview = data(request("overview").param("payment_method", "wechat"));
        JsonNode ledger = data(request("orders").param("payment_method", "wechat"));
        assertEquals(overview.at("/summary/sales_amount"), ledger.at("/totals/sales_amount"));
        assertEquals(overview.at("/summary/order_count"), ledger.at("/totals/order_count"));
        assertAmount("7", overview.at("/comparison/summary/sales_amount"));
        assertEquals(1, ledger.path("list").size());
        assertEquals("wechat", ledger.at("/list/0/payment_method").asText());
    }

    @Test
    void emptyRangeHasZeroTotalsAndEveryCalendarDay() throws Exception {
        JsonNode overview = data(request("overview"));
        assertAmount("0", overview.at("/summary/sales_amount"));
        assertAmount("0", overview.at("/summary/average_order"));
        assertEquals(0, overview.at("/summary/order_count").asInt());
        assertEquals(3, overview.path("trend").size());
        JsonNode ledger = data(request("orders"));
        assertEquals(0, ledger.path("list").size());
        assertEquals(0, ledger.at("/pagination/total").asInt());
    }

    @Test
    void ledgerStatusFilterKeepsTotalsAcrossAllPages() throws Exception {
        order("2026-02-10T12:00:00", "10.11", OrderStatus.PAID, "alipay", "One", 1);
        order("2026-02-11T12:00:00", "20.22", OrderStatus.PAID, "alipay", "Two", 1);
        order("2026-02-12T12:00:00", "40.00", OrderStatus.DELIVERED, "alipay", "Three", 1);
        JsonNode result = data(request("orders").param("status", "PAID").param("page", "2").param("page_size", "1"));
        assertEquals(1, result.path("list").size());
        assertEquals(2, result.at("/pagination/page").asInt());
        assertEquals(1, result.at("/pagination/page_size").asInt());
        assertEquals(2, result.at("/pagination/total").asInt());
        assertAmount("30.33", result.at("/totals/sales_amount"));
        assertEquals(2, result.at("/totals/order_count").asInt());
        assertAmount("10.11", result.at("/list/0/actual_amount"));
    }

    @Test
    void ledgerSortsByPaidTimeThenIdAndDoesNotExposeSecrets() throws Exception {
        Order first = order("2026-02-10T12:00:00", "1.00", OrderStatus.PAID, "alipay", "Zebra", 2);
        item(first, "Alpha", 3);
        Order second = order("2026-02-10T12:00:00", "2.00", OrderStatus.DELIVERED, "wechat", "Beta", 1);
        JsonNode result = data(request("orders"));
        // UUID database order is the canonical unsigned hexadecimal order.
        String expected = first.getId().toString().compareTo(second.getId().toString()) > 0 ? first.getId().toString() : second.getId().toString();
        assertEquals(expected, result.at("/list/0/id").asText());
        JsonNode row = result.path("list").get(first.getId().toString().equals(expected) ? 0 : 1);
        assertEquals(first.getEpayTradeNo(), row.path("payment_reference").asText());
        assertEquals("2026-02-10 12:00:00", row.path("paid_at").asText());
        assertTrue(row.hasNonNull("created_at"));
        assertEquals("Alpha", row.path("product_title").asText());
        assertEquals(5, row.path("item_count").asInt());
        assertEquals(9, row.size());
        assertFalse(result.toString().contains("never-expose"));
    }

    @Test
    void keywordMatchesOrderReferenceEmailAndProductWithoutDuplicateAmounts() throws Exception {
        Order order = order("2026-02-10T12:00:00", "12.34", OrderStatus.PAID, "alipay", "Special product", 1);
        item(order, "Special product second", 2);
        order.setEmail("buyer@example.invalid");
        order.setEpayTradeNo("Trade-MATCH-123");
        for (String keyword : List.of("special", "MATCH-123", "buyer@", order.getId().toString())) {
            JsonNode result = data(request("orders").param("keyword", keyword));
            assertEquals(1, result.at("/pagination/total").asInt(), keyword);
            assertAmount("12.34", result.at("/totals/sales_amount"));
        }
    }

    @Test
    void keywordTreatsSqlWildcardCharactersAsLiteralText() throws Exception {
        Order match = order("2026-02-10T12:00:00", "2.00", OrderStatus.PAID, "alipay", "100%_! product", 1);
        order("2026-02-11T12:00:00", "9.00", OrderStatus.PAID, "alipay", "100_other", 1);
        JsonNode result = data(request("orders").param("keyword", "%_!"));
        assertEquals(1, result.path("list").size());
        assertEquals(match.getId().toString(), result.at("/list/0/id").asText());
    }

    @Test
    void lowStockIncludesEnabledUndeletedProductsAndAvailableKeysOnly() throws Exception {
        Product low = product("Low", true, 0, 1);
        Product empty = product("Empty", true, 0, 0);
        Product enough = product("Enough", true, 0, 0);
        product("Hidden", false, 0, 10);
        product("Deleted", true, 1, 10);
        card(low, CardKeyStatus.AVAILABLE);
        card(low, CardKeyStatus.SOLD);
        card(enough, CardKeyStatus.AVAILABLE);
        JsonNode products = data(request("overview")).path("low_stock_products");
        assertEquals(2, products.size());
        JsonNode lowRow = Stream.of(products.get(0), products.get(1)).filter(p -> p.path("product_id").asText().equals(low.getId().toString())).findFirst().orElseThrow();
        assertEquals(1, lowRow.path("available_stock").asInt());
        assertEquals(1, lowRow.path("threshold").asInt());
        assertTrue(products.toString().contains(empty.getId().toString()));
    }

    @Test
    void lowStockSpecModeCountsOnlyUndeletedSameProductSpecPools() throws Exception {
        Product product = product("Spec pools", true, 0, 2);
        product.setSpecEnabled(true);
        ProductSpec first = spec(product, "First", 0);
        ProductSpec second = spec(product, "Second", 0);
        second.setVisible(false);
        ProductSpec deleted = spec(product, "Deleted", 1);
        Product foreign = product("Foreign", false, 0, 0);
        ProductSpec foreignSpec = spec(foreign, "Foreign spec", 0);
        card(product, CardKeyStatus.AVAILABLE).setSpecId(first.getId());
        card(product, CardKeyStatus.AVAILABLE).setSpecId(second.getId());
        card(product, CardKeyStatus.AVAILABLE);
        card(product, CardKeyStatus.AVAILABLE).setSpecId(deleted.getId());
        card(product, CardKeyStatus.AVAILABLE).setSpecId(UUID.randomUUID());
        card(product, CardKeyStatus.AVAILABLE).setSpecId(foreignSpec.getId());

        JsonNode products = data(request("overview")).path("low_stock_products");
        assertEquals(1, products.size());
        assertEquals(product.getId().toString(), products.get(0).path("product_id").asText());
        assertEquals(2, products.get(0).path("available_stock").asInt());
    }

    @Test
    void lowStockNonSpecModeCountsDefaultPoolOnceEvenWithExistingSpecs() throws Exception {
        Product product = product("Default pool", true, 0, 1);
        ProductSpec first = spec(product, "First", 0);
        ProductSpec second = spec(product, "Second", 0);
        card(product, CardKeyStatus.AVAILABLE);
        card(product, CardKeyStatus.AVAILABLE).setSpecId(first.getId());
        card(product, CardKeyStatus.AVAILABLE).setSpecId(second.getId());

        JsonNode products = data(request("overview")).path("low_stock_products");
        assertEquals(1, products.size());
        assertEquals(product.getId().toString(), products.get(0).path("product_id").asText());
        assertEquals(1, products.get(0).path("available_stock").asInt());
    }

    @Test
    void lowStockSpecEnabledWithoutLiveSpecsFallsBackToDefaultPool() throws Exception {
        Product product = product("Fallback pool", true, 0, 1);
        product.setSpecEnabled(true);
        ProductSpec deleted = spec(product, "Deleted", 1);
        card(product, CardKeyStatus.AVAILABLE);
        card(product, CardKeyStatus.AVAILABLE).setSpecId(deleted.getId());

        JsonNode products = data(request("overview")).path("low_stock_products");
        assertEquals(1, products.size());
        assertEquals(product.getId().toString(), products.get(0).path("product_id").asText());
        assertEquals(1, products.get(0).path("available_stock").asInt());
    }

    @Test
    void comparisonHandlesLeapDaysAndMaximumInclusiveRange() throws Exception {
        JsonNode leap = data(request("overview", "2024-02-28", "2024-03-01"));
        assertEquals("2024-02-25", leap.at("/comparison/start_date").asText());
        assertEquals("2024-02-27", leap.at("/comparison/end_date").asText());
        JsonNode year = data(request("overview", "2024-01-01", "2024-12-31"));
        assertEquals(366, year.path("trend").size());
    }

    @Test
    void exportUsesBomCnyShanghaiHeadersEscapingAndFormulaProtection() throws Exception {
        Order row = order("2026-02-10T12:00:00", "12.34", OrderStatus.PAID, "alipay", " =HYPERLINK(\"x\"),\r\ntext", 2);
        row.setEpayTradeNo("\t+SUM(1,2)");
        var response = mvc.perform(request("export")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}, new byte[]{bytes[0], bytes[1], bytes[2]});
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Asia/Shanghai"));
        assertTrue(csv.contains("CNY"));
        assertTrue(csv.contains("\"' =HYPERLINK(\"\"x\"\"),\r\ntext\""), csv);
        assertTrue(csv.contains("\"'\t+SUM(1,2)\""), csv);
        assertTrue(csv.contains("12.34"));
        assertFalse(csv.contains("never-expose"));
    }

    @Test
    void selectedExportIntersectsIdsWithAllFilters() throws Exception {
        Order wanted = order("2026-02-10T12:00:00", "3.00", OrderStatus.PAID, "alipay", "Wanted", 1);
        Order wrongChannel = order("2026-02-10T12:00:00", "7.00", OrderStatus.PAID, "wechat", "Other", 1);
        Order wrongDate = order("2026-02-13T00:00:00", "9.00", OrderStatus.PAID, "alipay", "Outside", 1);
        String csv = mvc.perform(request("export").param("payment_method", "alipay").param("ids", wanted.getId() + "," + wrongChannel.getId() + "," + wrongDate.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains(wanted.getId().toString()));
        assertFalse(csv.contains(wrongChannel.getId().toString()));
        assertFalse(csv.contains(wrongDate.getId().toString()));
    }

    @Test
    void exportRejectsOverTenThousandMatchingRowsInsteadOfTruncating() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 10001; i++) {
            rows.add(new Object[]{UUID.randomUUID(), LocalDateTime.parse("2026-02-10T12:00:00"), "PAID", "DIRECT", new BigDecimal("1.00")});
        }
        jdbc.batchUpdate("insert into orders (id, paid_at, status, order_type, actual_amount, points_deducted, is_risk_flagged) values (?, ?, ?, ?, ?, 0, false)", rows);
        mvc.perform(request("export")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("10000")));
    }

    @Test
    void emptyExportStillReturnsOnlyTheHeader() throws Exception {
        String csv = mvc.perform(request("export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, csv.lines().count());
    }

    @ParameterizedTest(name = "invalid {0}={1}")
    @MethodSource("invalidFilters")
    void invalidFiltersReturnExistingBadRequestEnvelope(String name, String value) throws Exception {
        var request = request("orders", name.equals("start_date") ? value : START, name.equals("end_date") ? value : END);
        if (!name.equals("start_date") && !name.equals("end_date")) request.param(name, value);
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
    }

    static Stream<Object[]> invalidFilters() {
        return Stream.of(
                new Object[]{"start_date", "2026-2-10"}, new Object[]{"start_date", "2026-02-30"},
                new Object[]{"start_date", ""}, new Object[]{"start_date", "2026-02-13"},
                new Object[]{"end_date", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString()},
                new Object[]{"start_date", "2025-02-10"}, new Object[]{"start_date", "0000-01-01"},
                new Object[]{"page", "0"}, new Object[]{"page", "-1"}, new Object[]{"page", "no"},
                new Object[]{"page", "2147483647"}, new Object[]{"page_size", "0"},
                new Object[]{"page_size", "101"}, new Object[]{"page_size", "no"},
                new Object[]{"status", "PENDING"}, new Object[]{"status", "paid"}, new Object[]{"status", "invalid"},
                new Object[]{"keyword", "x".repeat(101)}, new Object[]{"payment_method", "x".repeat(65)},
                new Object[]{"payment_method", "alipay\ninvalid"});
    }

    @Test
    void datesAreRequiredOnEveryRoute() throws Exception {
        for (String endpoint : List.of("overview", "orders", "export")) {
            mvc.perform(fresh(get("/admin/workbench/" + endpoint))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
        }
    }

    @Test
    void exportRejectsInvalidOrEmptySelectedIds() throws Exception {
        for (String ids : List.of("", "invalid", UUID.randomUUID() + ",", "x".repeat(400000))) {
            mvc.perform(request("export").param("ids", ids)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
        }
    }

    @Test
    void everyRouteRequiresAdministrator() throws Exception {
        for (String endpoint : List.of("overview", "orders", "export")) {
            mvc.perform(request(endpoint).with(anonymous())).andExpect(status().isUnauthorized());
            mvc.perform(request(endpoint).with(user("member").roles("USER"))).andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder request(String endpoint) {
        return request(endpoint, START, END);
    }

    private MockHttpServletRequestBuilder request(String endpoint, String start, String end) {
        return fresh(get("/admin/workbench/" + endpoint)).param("start_date", start).param("end_date", end);
    }

    private MockHttpServletRequestBuilder fresh(MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setRemoteAddr("192.0.2." + REQUEST_IP.incrementAndGet()); return r; });
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        entityManager.flush();
        return objectMapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private void assertAmount(String amount, JsonNode actual) {
        assertTrue(actual.isNumber(), actual.toString());
        assertEquals(0, new BigDecimal(amount).compareTo(actual.decimalValue()));
    }

    private Order order(String paidAt, String amount, OrderStatus status, String method, String title, int quantity) {
        Order order = new Order();
        order.setPaidAt(paidAt == null ? null : LocalDateTime.parse(paidAt));
        order.setActualAmount(amount == null ? null : new BigDecimal(amount));
        order.setTotalAmount(new BigDecimal("999.00"));
        order.setStatus(status);
        order.setOrderType(OrderType.DIRECT);
        order.setPaymentMethod(method);
        order.setEpayTradeNo("trade-" + UUID.randomUUID());
        order.setSessionToken("never-expose-session");
        order.setPaymentUrl("never-expose-payment-url");
        entityManager.persist(order);
        item(order, title, quantity);
        return order;
    }

    private void item(Order order, String title, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(UUID.randomUUID());
        item.setProductTitle(title);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.ONE);
        item.setSubtotal(BigDecimal.valueOf(quantity));
        entityManager.persist(item);
    }

    private Product product(String title, boolean enabled, int deleted, int threshold) {
        Product product = new Product();
        product.setTitle(title);
        product.setCategoryId(UUID.randomUUID());
        product.setBasePrice(BigDecimal.ONE);
        product.setEnabled(enabled);
        product.setIsDeleted(deleted);
        product.setLowStockThreshold(threshold);
        entityManager.persist(product);
        return product;
    }

    private ProductSpec spec(Product product, String name, int deleted) {
        ProductSpec spec = new ProductSpec();
        spec.setProductId(product.getId());
        spec.setName(name);
        spec.setPrice(BigDecimal.ONE);
        spec.setIsDeleted(deleted);
        entityManager.persist(spec);
        return spec;
    }

    private CardKey card(Product product, CardKeyStatus status) {
        CardKey key = new CardKey();
        key.setProductId(product.getId());
        key.setContent("never-expose-card");
        key.setStatus(status);
        entityManager.persist(key);
        return key;
    }
}

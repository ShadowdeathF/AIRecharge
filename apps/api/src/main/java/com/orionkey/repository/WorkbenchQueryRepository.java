package com.orionkey.repository;

import com.orionkey.constant.OrderStatus;
import com.orionkey.model.response.WorkbenchResponse.LowStockProduct;
import com.orionkey.model.response.WorkbenchResponse.Totals;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WorkbenchQueryRepository {

    private final EntityManager entityManager;

    public record Filter(LocalDateTime start, LocalDateTime endExclusive, String paymentMethod,
                         OrderStatus status, String keyword, List<UUID> ids) {}

    public record DailyTotals(LocalDate date, BigDecimal salesAmount, long orderCount, long undeliveredCount) {}

    public record OrderRow(UUID id, String paymentReference, LocalDateTime paidAt, LocalDateTime createdAt,
                           BigDecimal actualAmount, OrderStatus status, String paymentMethod) {}

    public record ItemSummary(String productTitle, long itemCount) {}

    public List<DailyTotals> dailyTotals(Filter filter) {
        return query("select cast(o.paidAt as LocalDate), coalesce(sum(o.actualAmount), 0), count(o), "
                        + "sum(case when o.status = com.orionkey.constant.OrderStatus.PAID then 1 else 0 end)", filter,
                " group by cast(o.paidAt as LocalDate) order by cast(o.paidAt as LocalDate)")
                .getResultList().stream().map(row -> new DailyTotals((LocalDate) row[0], (BigDecimal) row[1],
                        ((Number) row[2]).longValue(), ((Number) row[3]).longValue())).toList();
    }

    public Totals totals(Filter filter) {
        Object[] row = query("select coalesce(sum(o.actualAmount), 0), count(o)", filter, "").getSingleResult();
        return new Totals((BigDecimal) row[0], ((Number) row[1]).longValue());
    }

    public List<OrderRow> orders(Filter filter, int offset, int limit) {
        // Scalar projections deliberately omit payment URLs, sessions and card key material.
        return query("select o.id, o.epayTradeNo, o.paidAt, o.createdAt, coalesce(o.actualAmount, 0), o.status, o.paymentMethod",
                filter, " order by o.paidAt desc, o.id desc")
                .setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(row -> new OrderRow((UUID) row[0], (String) row[1], (LocalDateTime) row[2],
                        (LocalDateTime) row[3], (BigDecimal) row[4], (OrderStatus) row[5], (String) row[6])).toList();
    }

    public Map<UUID, ItemSummary> itemSummaries(List<UUID> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        List<Object[]> rows = entityManager.createQuery("select i.orderId, min(i.productTitle), sum(i.quantity) "
                        + "from OrderItem i where i.orderId in :ids group by i.orderId", Object[].class)
                .setParameter("ids", orderIds).getResultList();
        Map<UUID, ItemSummary> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((UUID) row[0], new ItemSummary((String) row[1], ((Number) row[2]).longValue()));
        }
        return result;
    }

    public List<LowStockProduct> lowStockProducts() {
        // Match the storefront's sellable pool: live specs in spec mode, otherwise the default pool.
        return entityManager.createQuery("select p.id, p.title, count(k.id), p.lowStockThreshold from Product p "
                        + "left join ProductSpec s on s.productId = p.id and s.isDeleted = 0 and p.specEnabled = true "
                        + "left join CardKey k on k.productId = p.id and k.status = com.orionkey.constant.CardKeyStatus.AVAILABLE "
                        + "and (k.specId = s.id or (s.id is null and k.specId is null)) "
                        + "where p.enabled = true and p.isDeleted = 0 group by p.id, p.title, p.lowStockThreshold "
                        + "having count(k.id) <= p.lowStockThreshold order by count(k.id), p.title, p.id", Object[].class)
                .getResultList().stream().map(row -> new LowStockProduct((UUID) row[0], (String) row[1],
                        ((Number) row[2]).longValue(), ((Number) row[3]).intValue())).toList();
    }

    private TypedQuery<Object[]> query(String select, Filter filter, String suffix) {
        StringBuilder hql = new StringBuilder(select).append(" from Order o where o.paidAt >= :start and o.paidAt < :end ")
                .append("and o.status in (com.orionkey.constant.OrderStatus.PAID, com.orionkey.constant.OrderStatus.DELIVERED)");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("start", filter.start());
        parameters.put("end", filter.endExclusive());
        if (filter.paymentMethod() != null) {
            hql.append(" and o.paymentMethod = :paymentMethod");
            parameters.put("paymentMethod", filter.paymentMethod());
        }
        if (filter.status() != null) {
            hql.append(" and o.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.keyword() != null) {
            hql.append(" and (lower(str(o.id)) like :keyword escape '!' or lower(o.epayTradeNo) like :keyword escape '!' ")
                    .append("or lower(o.email) like :keyword escape '!' or exists (select 1 from OrderItem i ")
                    .append("where i.orderId = o.id and lower(i.productTitle) like :keyword escape '!'))");
            String literal = filter.keyword().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parameters.put("keyword", "%" + literal + "%");
        }
        if (filter.ids() != null) {
            hql.append(" and o.id in :ids");
            parameters.put("ids", filter.ids());
        }
        TypedQuery<Object[]> query = entityManager.createQuery(hql.append(suffix).toString(), Object[].class);
        parameters.forEach(query::setParameter);
        return query;
    }
}

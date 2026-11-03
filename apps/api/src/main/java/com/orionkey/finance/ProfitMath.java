package com.orionkey.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Exact settlement calculations. Documents keep the unrounded basis for later corrections. */
public final class ProfitMath {
    private ProfitMath() {}
    public record ProfitLine(BigDecimal profit, UUID referrer) {}
    public record Weight(UUID personId, BigDecimal weight) {}
    public record Distribution(Map<UUID, BigDecimal> precisePeople, Map<UUID, BigDecimal> people,
            BigDecimal profit, BigDecimal operatingBase, BigDecimal roundingAdjustment,
            BigDecimal operating, BigDecimal dividend) {}

    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    public static Distribution calculate(List<ProfitLine> lines, Map<String, UUID> roles) {
        for (String role : List.of("TECH", "BOSS", "FINANCE"))
            if (roles.get(role) == null) throw new IllegalArgumentException("基础分成人员未配置完整");
        Map<UUID, BigDecimal> raw = new LinkedHashMap<>();
        BigDecimal profit = BigDecimal.ZERO, noReferrer = BigDecimal.ZERO;
        for (ProfitLine line : lines) {
            if (line.profit() == null) throw new IllegalArgumentException("利润尚未确认");
            profit = profit.add(line.profit());
            raw.merge(roles.get("TECH"), line.profit().multiply(new BigDecimal("0.36")), BigDecimal::add);
            raw.merge(roles.get("BOSS"), line.profit().multiply(new BigDecimal("0.2925")), BigDecimal::add);
            raw.merge(roles.get("FINANCE"), line.profit().multiply(new BigDecimal("0.1125")), BigDecimal::add);
            if (line.referrer() == null) noReferrer = noReferrer.add(line.profit());
            else raw.merge(line.referrer(), line.profit().multiply(new BigDecimal("0.135")), BigDecimal::add);
        }
        // Include zero balances when a full refund reverses an earlier settlement.
        for (UUID id : roles.values()) raw.putIfAbsent(id, BigDecimal.ZERO);
        Map<UUID, BigDecimal> rounded = new LinkedHashMap<>();
        raw.forEach((person, value) -> rounded.put(person, money(value)));
        BigDecimal base = money(profit.multiply(new BigDecimal("0.10")));
        BigDecimal dividend = money(noReferrer.multiply(new BigDecimal("0.135")));
        BigDecimal personal = rounded.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tail = money(profit).subtract(base).subtract(dividend).subtract(personal);
        return new Distribution(Collections.unmodifiableMap(raw), Collections.unmodifiableMap(rounded),
                money(profit), base, tail, base.add(tail), dividend);
    }

    /** Largest remainders, with explicit recipient order breaking cent ties. Also handles clawbacks. */
    public static Map<UUID, BigDecimal> weighted(BigDecimal amount, List<Weight> weights) {
        if (amount == null || amount.scale() > 2 || weights.isEmpty()) throw new IllegalArgumentException("分红金额或权重无效");
        Set<UUID> people = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Weight weight : weights) {
            if (weight.personId() == null || !people.add(weight.personId()) || weight.weight() == null || weight.weight().signum() <= 0)
                throw new IllegalArgumentException("分红人员不得重复，权重必须大于零");
            sum = sum.add(weight.weight());
        }
        BigDecimal cents = amount.abs().movePointRight(2);
        List<BigDecimal> units = new ArrayList<>(), remainders = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Weight weight : weights) {
            BigDecimal numerator = cents.multiply(weight.weight());
            BigDecimal floor = numerator.divideToIntegralValue(sum);
            units.add(floor); remainders.add(numerator.subtract(floor.multiply(sum))); allocated = allocated.add(floor);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) order.add(i);
        order.sort(Comparator.<Integer, BigDecimal>comparing(remainders::get).reversed().thenComparingInt(i -> i));
        int extra = cents.subtract(allocated).intValueExact();
        for (int i = 0; i < extra; i++) { int index = order.get(i); units.set(index, units.get(index).add(BigDecimal.ONE)); }
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < weights.size(); i++) result.put(weights.get(i).personId(), units.get(i).movePointLeft(2).setScale(2).multiply(BigDecimal.valueOf(amount.signum())));
        return result;
    }
}

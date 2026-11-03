package com.orionkey.finance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfitMathTests {
    private final UUID tech = new UUID(0, 1), boss = new UUID(0, 2), finance = new UUID(0, 3);
    private final Map<String, UUID> roles = Map.of("TECH", tech, "BOSS", boss, "FINANCE", finance);
    private BigDecimal d(String value) { return new BigDecimal(value); }
    private ProfitMath.Distribution calculate(String profit, UUID referrer) {
        return ProfitMath.calculate(List.of(new ProfitMath.ProfitLine(d(profit), referrer)), roles);
    }
    private void equal(String expected, BigDecimal actual) { assertEquals(0, d(expected).compareTo(actual)); }

    @Test void reproducesCalculatorAndShowsCompanyRoundingAdjustment() {
        var result = calculate("262.28", tech);
        equal("129.83", result.people().get(tech));
        equal("76.72", result.people().get(boss));
        equal("29.51", result.people().get(finance));
        equal("26.23", result.operatingBase());
        equal("-0.01", result.roundingAdjustment());
        equal("262.28", result.operating().add(result.dividend()).add(result.people().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    @Test void absentReferrerAccruesQuarterDividendRatherThanPersonalReward() {
        var result = calculate("100", null);
        equal("36", result.people().get(tech)); equal("29.25", result.people().get(boss));
        equal("11.25", result.people().get(finance)); equal("10", result.operating()); equal("13.50", result.dividend());
    }
    @Test void lossesRemainNegativeAndConserveMoney() {
        var result = calculate("-12.04", boss);
        assertTrue(result.people().get(boss).signum() < 0);
        equal("-12.04", result.operating().add(result.dividend()).add(result.people().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    @Test void multipleOrdersAccumulatePreciselyBeforeRounding() {
        var lines = new ArrayList<ProfitMath.ProfitLine>();
        for (int i = 0; i < 10; i++) lines.add(new ProfitMath.ProfitLine(d("0.01"), tech));
        equal("0.05", ProfitMath.calculate(lines, roles).people().get(tech));
    }
    @Test void laterReferralUsesCumulativePersonTargetWithoutAnotherCent() {
        var before = calculate("0.05", null); var after = calculate("0.05", tech);
        equal("0.02", before.people().get(tech)); equal("0.02", after.people().get(tech));
        equal("0", after.people().get(tech).subtract(before.people().get(tech)));
    }
    @Test void externalReferrersReceiveOnlyTheirReward() {
        UUID external = new UUID(0, 9); var result = calculate("100", external);
        equal("13.50", result.people().get(external)); equal("36", result.people().get(tech));
    }
    @Test void refundRecalculatesOriginalTargetsAndCanCreatePersonalDebt() {
        var before = calculate("100", tech); var after = calculate("0", tech);
        equal("-49.50", after.people().get(tech).subtract(before.people().get(tech)));
    }
    @Test void quarterlyEqualWeightsConserveIndivisibleCents() {
        var result = ProfitMath.weighted(d("10.00"), List.of(new ProfitMath.Weight(tech,d("1")), new ProfitMath.Weight(boss,d("1")), new ProfitMath.Weight(finance,d("1"))));
        equal("3.34", result.get(tech)); equal("3.33", result.get(boss)); equal("3.33", result.get(finance));
    }
    @Test void quarterlyCustomWeightsAndNegativeCorrectionsAreExact() {
        var weights = List.of(new ProfitMath.Weight(tech,d("2")), new ProfitMath.Weight(boss,d("1")), new ProfitMath.Weight(finance,d("1")));
        equal("5", ProfitMath.weighted(d("10"), weights).get(tech));
        equal("-5", ProfitMath.weighted(d("-10"), weights).get(tech));
    }
    @Test void rejectsIncompleteRolesAndAmbiguousQuarterWeights() {
        assertThrows(IllegalArgumentException.class, () -> ProfitMath.calculate(List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ProfitMath.weighted(d("1"), List.of(new ProfitMath.Weight(tech,d("0")))));
        assertThrows(IllegalArgumentException.class, () -> ProfitMath.weighted(d("1"), List.of(new ProfitMath.Weight(tech,d("1")), new ProfitMath.Weight(tech,d("1")))));
    }
}

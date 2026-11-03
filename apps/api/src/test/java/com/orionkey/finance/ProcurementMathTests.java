package com.orionkey.finance;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementMathTests {
    @Test void quantityOneSupplementUsesFullReceiptCost() {
        assertEquals(new BigDecimal("112.04"), ProcurementMath.issueCost(new BigDecimal("112.04"), 1, 0, 1));
    }
    @Test void repeatedIssuesExactlyExhaustReceiptIncludingLastCent() {
        BigDecimal total = BigDecimal.ZERO;
        for (int previous = 0; previous < 3; previous++) total = total.add(ProcurementMath.issueCost(new BigDecimal("0.01"), 3, previous, 1));
        assertEquals(new BigDecimal("0.01"), total);
    }
    @Test void partialIssueHasSameCostAsEquivalentCumulativeIssue() {
        var total = new BigDecimal("100.00");
        assertEquals(ProcurementMath.issueCost(total, 7, 0, 5), ProcurementMath.issueCost(total, 7, 0, 2).add(ProcurementMath.issueCost(total, 7, 2, 3)));
    }
    @Test void refusesOversellingAndInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> ProcurementMath.issueCost(new BigDecimal("1"), 3, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> ProcurementMath.issueCost(new BigDecimal("-1"), 3, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ProcurementMath.issueCost(new BigDecimal("1.001"), 3, 0, 1));
    }
}

package com.orionkey.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProcurementMath {
    private ProcurementMath() {}
    public static BigDecimal issueCost(BigDecimal totalCost, int totalQuantity, int previous, int quantity) {
        if (totalCost == null || totalCost.signum() < 0 || totalCost.scale() > 2 || totalQuantity <= 0
                || previous < 0 || quantity <= 0 || (long) previous + quantity > totalQuantity)
            throw new IllegalArgumentException("采购成本、数量或剩余库存无效");
        BigDecimal total = BigDecimal.valueOf(totalQuantity);
        BigDecimal before = totalCost.multiply(BigDecimal.valueOf(previous)).divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal after = totalCost.multiply(BigDecimal.valueOf((long) previous + quantity)).divide(total, 2, RoundingMode.HALF_UP);
        return after.subtract(before);
    }
}

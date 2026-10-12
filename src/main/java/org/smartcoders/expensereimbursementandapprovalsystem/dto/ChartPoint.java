package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.math.BigDecimal;

public record ChartPoint(
        String label,
        BigDecimal total,
        long count
) {
}
package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsResponse(
        String currency,
        BigDecimal grandTotal,
        long claimCount,
        List<ChartPoint> byCategory,
        List<ChartPoint> byMonth,
        List<ChartPoint> byDepartment,
        List<ChartPoint> byStatus
) {
}
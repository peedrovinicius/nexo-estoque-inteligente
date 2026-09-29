package br.com.nexoestoque.model;

import java.math.BigDecimal;

public final class DemandPlanning {
    private DemandPlanning() {}

    public record PlanningItem(
            long productId,
            String sku,
            String productName,
            String category,
            BigDecimal currentStock,
            BigDecimal reservedQuantity,
            BigDecimal incomingQuantity,
            BigDecimal availableToPromise,
            BigDecimal demand7Days,
            BigDecimal demand30Days,
            BigDecimal demand90Days,
            BigDecimal forecastDailyDemand,
            BigDecimal dailyDemandStdDev,
            int supplierLeadTimeDays,
            BigDecimal safetyStock,
            BigDecimal reorderPoint,
            BigDecimal targetStock,
            BigDecimal projectedAtLeadTime,
            BigDecimal recommendedQuantity,
            String riskLevel,
            String confidenceLevel
    ) {}

    public record PlanningSummary(
            int totalProducts,
            int criticalProducts,
            int highRiskProducts,
            BigDecimal recommendedUnits,
            BigDecimal projectedShortageUnits
    ) {}
}

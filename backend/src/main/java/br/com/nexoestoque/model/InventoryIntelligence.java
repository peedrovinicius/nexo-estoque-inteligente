package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InventoryIntelligence {
    private InventoryIntelligence() {}

    public record AbcItem(long productId, String sku, String productName, String category,
                          BigDecimal stockQuantity, BigDecimal stockValue, BigDecimal participationPercent,
                          BigDecimal cumulativePercent, String abcClass) {}

    public record SlowMovingItem(long productId, String sku, String productName, BigDecimal currentStock,
                                 BigDecimal stockValue, LocalDateTime lastExitAt, Integer daysSinceLastExit) {}

    public record CoverageItem(long productId, String sku, String productName, BigDecimal currentStock,
                               BigDecimal exitQuantity, BigDecimal averageDailyConsumption,
                               BigDecimal coverageDays, String coverageLevel) {}

    public record OpenPurchaseAgingItem(long orderId, long supplierId, String supplierName, String status,
                                        LocalDate expectedAt, int daysOpen, int overdueDays,
                                        BigDecimal pendingQuantity, BigDecimal pendingValue) {}
    public record ExpiryExposureSummary(
            int horizonDays,
            int expiredBatches,
            BigDecimal expiredQuantity,
            BigDecimal expiredValue,
            int criticalBatches,
            BigDecimal criticalQuantity,
            BigDecimal criticalValue,
            int warningBatches,
            BigDecimal warningQuantity,
            BigDecimal warningValue
    ) {}

    public record CapitalBreakdownItem(
            String dimension,
            String key,
            String label,
            BigDecimal stockQuantity,
            BigDecimal stockValue,
            BigDecimal participationPercent
    ) {}

    public record AlertSettings(
            int expiryWarningDays,
            int lowCoverageDays,
            int slowMovingDays,
            int purchaseOverdueDays,
            int coverageWindowDays
    ) {}

    public record OperationalAlert(
            String key,
            String type,
            String severity,
            String title,
            String description,
            String value
    ) {}
}

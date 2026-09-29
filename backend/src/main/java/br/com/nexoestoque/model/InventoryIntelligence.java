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
}

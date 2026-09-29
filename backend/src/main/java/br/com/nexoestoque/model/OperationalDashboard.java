package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OperationalDashboard(
        BigDecimal totalStock,
        BigDecimal stockValue,
        int activeProducts,
        int criticalProducts,
        int outOfStockProducts,
        int expiryRiskBatches,
        int expiredBatches,
        int expiryWarningBatches,
        BigDecimal expiryRiskValue,
        BigDecimal inventoryAccuracy,
        Integer inventoryDivergences,
        CriticalStockItem topCritical,
        ExpiryRiskItem topExpiry,
        Long openInventoryId,
        String openInventoryName,
        int openInventoryCountedItems
) {
    public record CriticalStockItem(
            long productId,
            String sku,
            String name,
            BigDecimal currentStock,
            BigDecimal minimumStock,
            BigDecimal deficit,
            BigDecimal estimatedReplenishmentCost
    ) {}

    public record ExpiryRiskItem(
            long batchId,
            long productId,
            String sku,
            String productName,
            String lotCode,
            LocalDate expiresAt,
            int daysToExpiry,
            String riskLevel,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal exposureValue,
            long warehouseId,
            String warehouseName,
            long locationId,
            String locationCode
    ) {}

    public record StockPositionItem(
            long batchId,
            long productId,
            String sku,
            String barcode,
            String productName,
            String category,
            String lotCode,
            long warehouseId,
            String warehouseCode,
            String warehouseName,
            long locationId,
            String locationCode,
            String aisle,
            String shelf,
            String binCode,
            LocalDate expiresAt,
            Integer daysToExpiry,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal stockValue
    ) {}
}

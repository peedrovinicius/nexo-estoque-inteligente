package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class TraceabilityIntelligence {
    private TraceabilityIntelligence() {}

    public record SupplierPerformanceItem(
            long supplierId,
            String supplierName,
            int orderCount,
            int receivedOrderCount,
            int openOrderCount,
            int delayedOpenOrderCount,
            BigDecimal orderedQuantity,
            BigDecimal receivedQuantity,
            BigDecimal fillRatePercent,
            BigDecimal averageLeadTimeDays,
            BigDecimal onTimeRatePercent,
            BigDecimal purchasedValue
    ) {}

    public record StockFlowItem(
            long productId,
            String sku,
            String productName,
            String category,
            BigDecimal currentStock,
            BigDecimal entryQuantity,
            BigDecimal exitQuantity,
            BigDecimal returnQuantity,
            BigDecimal adjustmentQuantity,
            BigDecimal netFlow,
            int movementCount,
            LocalDateTime lastMovementAt
    ) {}

    public record MovementLedgerItem(
            long movementId,
            String source,
            long productId,
            String sku,
            String productName,
            String lots,
            String movementType,
            BigDecimal quantity,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String reason,
            String performedBy,
            LocalDateTime createdAt
    ) {}

    public record LotTraceEvent(
            String eventType,
            long productId,
            String sku,
            String productName,
            String lotCode,
            BigDecimal quantity,
            String origin,
            String destination,
            String reference,
            String actor,
            LocalDateTime createdAt
    ) {}

    public record IntegrityIssue(
            String key,
            String severity,
            String type,
            String title,
            String detail
    ) {}

    public record InventoryIntegrityReport(
            LocalDateTime checkedAt,
            int stockMismatchCount,
            int negativeProductBalanceCount,
            int negativeBatchBalanceCount,
            int inactiveProductWithStockCount,
            int receiptMismatchCount,
            List<IntegrityIssue> issues
    ) {}
}

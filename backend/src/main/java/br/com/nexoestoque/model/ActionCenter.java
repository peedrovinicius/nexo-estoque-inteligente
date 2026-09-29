package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ActionCenter {
    private ActionCenter() {}

    public record ReplenishmentSuggestion(
            long productId,
            String sku,
            String productName,
            String category,
            BigDecimal currentStock,
            BigDecimal minimumStock,
            BigDecimal reservedQuantity,
            BigDecimal incomingQuantity,
            BigDecimal availableToPromise,
            BigDecimal averageDailyDemand,
            Long supplierId,
            String supplierName,
            int supplierLeadTimeDays,
            BigDecimal referenceUnitCost,
            BigDecimal recommendedQuantity,
            BigDecimal estimatedCost,
            String riskLevel,
            boolean supplierRequired
    ) {}

    public record BatchDraftItem(
            long purchaseOrderId,
            long supplierId,
            String supplierName,
            int itemCount,
            BigDecimal totalAmount
    ) {}

    public record BatchDraftResult(
            String ruleVersion,
            int requestedProducts,
            int createdOrders,
            List<BatchDraftItem> orders
    ) {}

    public record PurchaseApprovalItem(
            long purchaseOrderId,
            String supplierName,
            BigDecimal totalAmount,
            String createdBy,
            LocalDateTime createdAt,
            String approvalStatus,
            String requestedBy,
            LocalDateTime requestedAt,
            String decidedBy,
            LocalDateTime decidedAt,
            String decisionReason
    ) {}

    public record StockReservation(
            long id,
            long productId,
            String sku,
            String productName,
            BigDecimal quantity,
            String referenceCode,
            String notes,
            String status,
            String reservedBy,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            BigDecimal productStock,
            BigDecimal activeReservedQuantity,
            BigDecimal availableToPromise
    ) {}

    public record DailyActionItem(
            String key,
            String type,
            String severity,
            String title,
            String description,
            String value,
            String actionTarget
    ) {}

    public record DailyActionQueue(
            LocalDateTime generatedAt,
            int criticalCount,
            int warningCount,
            int infoCount,
            List<DailyActionItem> items
    ) {}
}

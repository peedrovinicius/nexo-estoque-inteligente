package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class QualityControl {
    private QualityControl() {}

    public record BatchQualityState(
            long batchId,
            long productId,
            String sku,
            String productName,
            String lotCode,
            String warehouseName,
            String locationCode,
            BigDecimal quantity,
            String qualityStatus,
            String qualityReason,
            String qualityUpdatedBy,
            LocalDateTime qualityUpdatedAt
    ) {}

    public record LotRecall(
            long id,
            long productId,
            String sku,
            String productName,
            String lotCode,
            String reason,
            String status,
            String createdBy,
            LocalDateTime createdAt,
            String closedBy,
            LocalDateTime closedAt,
            String resolution,
            BigDecimal currentQuantity,
            int affectedBatchCount
    ) {}

    public record RecallBatchLocation(
            long batchId,
            String warehouseName,
            String locationCode,
            BigDecimal quantitySnapshot,
            BigDecimal currentQuantity,
            String currentQualityStatus
    ) {}

    public record RecallImpact(
            LotRecall recall,
            BigDecimal currentQuantity,
            BigDecimal receiptQuantity,
            BigDecimal exitedQuantity,
            int affectedLocations,
            LocalDateTime firstReceiptAt,
            LocalDateTime lastExitAt,
            List<RecallBatchLocation> batches
    ) {}

    public record ReceiptVariance(
            long id,
            long purchaseOrderId,
            long purchaseOrderItemId,
            Long purchaseReceiptId,
            long productId,
            String sku,
            String productName,
            String supplierName,
            String varianceType,
            BigDecimal quantity,
            String reason,
            String reportedBy,
            LocalDateTime createdAt
    ) {}

    public record QualitySummary(
            int quarantinedBatches,
            int blockedBatches,
            int openRecalls,
            int receiptVariances30Days,
            BigDecimal heldQuantity
    ) {}
}

package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseReceiptResult(
        Long receiptId,
        Long purchaseOrderId,
        Long purchaseOrderItemId,
        Long productId,
        Long batchId,
        Long stockMovementId,
        Long locationId,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String orderStatus,
        String receivedBy,
        LocalDateTime createdAt
) {}

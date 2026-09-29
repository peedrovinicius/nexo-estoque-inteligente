package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockTransferResult(
        Long id,
        Long productId,
        Long sourceBatchId,
        Long destinationBatchId,
        Long sourceLocationId,
        Long destinationLocationId,
        BigDecimal quantity,
        String reason,
        String performedBy,
        LocalDateTime createdAt
) {}

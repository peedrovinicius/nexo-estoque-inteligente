package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record BatchAdjustmentRequest(
        Long productId,
        Long batchId,
        BigDecimal quantityDelta,
        String reason,
        String idempotencyKey
) {}

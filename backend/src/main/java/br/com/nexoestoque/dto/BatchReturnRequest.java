package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record BatchReturnRequest(
        Long productId,
        Long batchId,
        BigDecimal quantity,
        String reason,
        String idempotencyKey
) {}

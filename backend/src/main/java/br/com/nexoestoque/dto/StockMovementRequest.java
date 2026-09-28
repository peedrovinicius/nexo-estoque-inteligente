package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record StockMovementRequest(
        Long productId,
        String movementType,
        BigDecimal quantity,
        String reason
) {}

package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockMovement(
        Long id,
        Long productId,
        String productName,
        String movementType,
        BigDecimal quantity,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String reason,
        String performedBy,
        LocalDateTime createdAt
) {}

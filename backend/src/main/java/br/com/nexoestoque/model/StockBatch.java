package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockBatch(
        Long id,
        Long productId,
        String sku,
        String productName,
        String lotCode,
        LocalDate expiresAt,
        BigDecimal quantity,
        BigDecimal unitCost,
        LocalDateTime receivedAt,
        Integer daysToExpiry,
        String expiryStatus,
        Integer fefoPosition
) {}

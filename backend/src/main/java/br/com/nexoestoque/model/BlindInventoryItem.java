package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BlindInventoryItem(
        Long productId,
        String sku,
        String productName,
        BigDecimal countedQuantity,
        BigDecimal systemQuantitySnapshot,
        BigDecimal differenceQuantity,
        LocalDateTime countedAt,
        boolean revealed
) {}

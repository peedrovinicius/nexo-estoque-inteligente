package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record BlindInventoryCountRequest(
        Long productId,
        BigDecimal countedQuantity
) {}

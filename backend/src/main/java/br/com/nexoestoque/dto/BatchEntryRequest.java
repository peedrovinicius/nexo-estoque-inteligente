package br.com.nexoestoque.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BatchEntryRequest(
        Long productId,
        String lotCode,
        LocalDate expiresAt,
        BigDecimal quantity,
        BigDecimal unitCost,
        String reason
) {}

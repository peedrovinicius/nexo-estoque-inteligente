package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record FefoExitRequest(
        Long productId,
        BigDecimal quantity,
        String reason
) {}

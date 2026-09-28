package br.com.nexoestoque.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FefoPreviewAllocation(
        Long batchId,
        String lotCode,
        LocalDate expiresAt,
        BigDecimal availableQuantity,
        BigDecimal allocatedQuantity,
        Integer fefoPosition
) {}

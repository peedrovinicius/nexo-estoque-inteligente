package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovementAllocation(
        Long id,
        Long movementId,
        Long batchId,
        String lotCode,
        LocalDate expiresAt,
        BigDecimal quantity
) {}

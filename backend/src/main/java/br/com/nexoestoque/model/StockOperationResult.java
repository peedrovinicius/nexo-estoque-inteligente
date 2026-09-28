package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.util.List;

public record StockOperationResult(
        Long movementId,
        Long batchId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        List<MovementAllocation> allocations
) {}

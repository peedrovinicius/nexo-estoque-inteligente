package br.com.nexoestoque.dto;

import java.math.BigDecimal;
import java.util.List;

public record FefoPreviewResponse(
        Long productId,
        BigDecimal requestedQuantity,
        BigDecimal availableQuantity,
        boolean sufficient,
        List<FefoPreviewAllocation> allocations
) {}

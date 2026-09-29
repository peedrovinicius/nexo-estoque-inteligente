package br.com.nexoestoque.dto;

import java.math.BigDecimal;

public record ReplenishmentOrderResponse(
        Long purchaseOrderId,
        String riskLevel,
        BigDecimal recommendedQuantity,
        String ruleVersion
) {}

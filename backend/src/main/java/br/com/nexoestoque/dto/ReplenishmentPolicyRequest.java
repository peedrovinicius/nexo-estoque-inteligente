package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReplenishmentPolicyRequest(
        boolean enabled,

        @Min(value = 1, message = "Cobertura alvo mínima é 1 dia")
        @Max(value = 365, message = "Cobertura alvo máxima é 365 dias")
        int targetCoverageDays,

        @NotNull(message = "Multiplicador de segurança é obrigatório")
        @DecimalMin(value = "0.0", message = "Multiplicador não pode ser negativo")
        @DecimalMax(value = "10.0", message = "Multiplicador máximo é 10")
        BigDecimal safetyStockMultiplier,

        @NotNull(message = "Pedido mínimo é obrigatório")
        @DecimalMin(value = "0.001", message = "Pedido mínimo deve ser maior que zero")
        BigDecimal minimumOrderQuantity,

        @NotNull(message = "Múltiplo de compra é obrigatório")
        @DecimalMin(value = "0.001", message = "Múltiplo deve ser maior que zero")
        BigDecimal orderMultiple,

        @Positive(message = "Fornecedor inválido")
        Long preferredSupplierId
) {}

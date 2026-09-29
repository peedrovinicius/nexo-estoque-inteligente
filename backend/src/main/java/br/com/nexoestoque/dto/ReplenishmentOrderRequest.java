package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReplenishmentOrderRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotNull(message = "Fornecedor é obrigatório")
        @Positive(message = "Fornecedor inválido")
        Long supplierId,

        @NotNull(message = "Demanda média diária é obrigatória")
        @DecimalMin(value = "0.01", message = "Demanda média deve ser maior que zero")
        BigDecimal averageDailyDemand,

        @NotNull(message = "Variação de demanda é obrigatória")
        @DecimalMin(value = "0.0", message = "Variação de demanda não pode ser negativa")
        BigDecimal demandVariationPercent,

        @Min(value = 0, message = "Atraso do fornecedor não pode ser negativo")
        int supplierDelayDays,

        @NotNull(message = "Compra planejada é obrigatória")
        @DecimalMin(value = "0.0", message = "Compra planejada não pode ser negativa")
        BigDecimal plannedPurchase
) {}

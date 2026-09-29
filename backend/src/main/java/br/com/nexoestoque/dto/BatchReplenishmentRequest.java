package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record BatchReplenishmentRequest(
        @NotEmpty(message = "Selecione ao menos um produto")
        List<@NotNull Long> productIds,

        @Min(value = 7, message = "Janela mínima é 7 dias")
        @Max(value = 365, message = "Janela máxima é 365 dias")
        int windowDays,

        @NotNull(message = "Variação de demanda é obrigatória")
        @DecimalMin(value = "0.0", message = "Variação de demanda não pode ser negativa")
        BigDecimal demandVariationPercent
) {}

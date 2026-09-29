package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BlindInventoryCountRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotNull(message = "Contagem é obrigatória")
        @DecimalMin(value = "0.000", message = "A contagem não pode ser negativa")
        BigDecimal countedQuantity
) {}

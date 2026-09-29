package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantity,

        @DecimalMin(value = "0.00", message = "Custo não pode ser negativo")
        BigDecimal unitCost
) {}

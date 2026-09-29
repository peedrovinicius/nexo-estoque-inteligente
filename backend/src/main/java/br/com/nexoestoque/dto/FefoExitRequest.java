package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FefoExitRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantity,

        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason,

        @NotBlank(message = "Chave de idempotência é obrigatória")
        @Size(min = 8, max = 64, message = "Chave de idempotência deve ter entre 8 e 64 caracteres")
        String idempotencyKey
) {}

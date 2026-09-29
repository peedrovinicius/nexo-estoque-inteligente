package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockMovementRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotBlank(message = "Tipo de movimentação é obrigatório")
        @Pattern(regexp = "^(?i:ENTRY|EXIT|ADJUSTMENT|RETURN)$", message = "Tipo de movimentação inválido")
        String movementType,

        @NotNull(message = "Quantidade é obrigatória")
        BigDecimal quantity,

        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason,

        @NotBlank(message = "Chave de idempotência é obrigatória")
        @Size(min = 8, max = 64, message = "Chave de idempotência deve ter entre 8 e 64 caracteres")
        String idempotencyKey
) {}

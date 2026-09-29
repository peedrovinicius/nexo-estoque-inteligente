package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockReservationRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantity,

        @NotBlank(message = "Referência é obrigatória")
        @Size(max = 120, message = "Referência deve ter no máximo 120 caracteres")
        String referenceCode,

        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
        String notes,

        LocalDateTime expiresAt
) {}

package br.com.nexoestoque.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record OperationalExceptionRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotBlank(message = "Tipo é obrigatório")
        String exceptionType,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason,

        @NotNull(message = "Validade é obrigatória")
        @Future(message = "Validade deve estar no futuro")
        LocalDateTime expiresAt
) {}

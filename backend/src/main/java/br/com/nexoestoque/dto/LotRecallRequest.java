package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LotRecallRequest(
        @NotNull(message = "Produto é obrigatório")
        @Positive(message = "Produto inválido")
        Long productId,

        @NotBlank(message = "Lote é obrigatório")
        @Size(max = 80, message = "Lote deve ter no máximo 80 caracteres")
        String lotCode,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason
) {}

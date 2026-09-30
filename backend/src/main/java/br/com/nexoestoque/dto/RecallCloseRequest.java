package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecallCloseRequest(
        @NotBlank(message = "Resolução é obrigatória")
        @Size(max = 255, message = "Resolução deve ter no máximo 255 caracteres")
        String resolution
) {}

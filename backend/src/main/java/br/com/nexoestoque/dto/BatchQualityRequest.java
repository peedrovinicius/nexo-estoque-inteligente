package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BatchQualityRequest(
        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason
) {}

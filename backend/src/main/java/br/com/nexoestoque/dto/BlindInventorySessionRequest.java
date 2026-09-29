package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlindInventorySessionRequest(
        @NotBlank(message = "Nome do inventário é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String name
) {}

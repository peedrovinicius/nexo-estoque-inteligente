package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
        @NotNull(message = "Status ativo/inativo é obrigatório")
        Boolean active
) {}

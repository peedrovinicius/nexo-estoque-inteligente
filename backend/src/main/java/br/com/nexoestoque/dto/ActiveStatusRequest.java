package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotNull;

public record ActiveStatusRequest(
        @NotNull(message = "Status é obrigatório")
        Boolean active
) {}

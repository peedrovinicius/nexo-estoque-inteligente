package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockLocationRequest(
        @NotNull(message = "Depósito é obrigatório")
        @Positive(message = "Depósito inválido")
        Long warehouseId,

        @NotBlank(message = "Código da posição é obrigatório")
        @Size(max = 60, message = "Código deve ter no máximo 60 caracteres")
        String code,

        @Size(max = 60, message = "Corredor deve ter no máximo 60 caracteres")
        String aisle,

        @Size(max = 60, message = "Prateleira deve ter no máximo 60 caracteres")
        String shelf,

        @Size(max = 60, message = "Posição deve ter no máximo 60 caracteres")
        String binCode
) {}

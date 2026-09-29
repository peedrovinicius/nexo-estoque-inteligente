package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank(message = "Código do depósito é obrigatório")
        @Size(max = 40, message = "Código deve ter no máximo 40 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Código contém caracteres inválidos")
        String code,

        @NotBlank(message = "Nome do depósito é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String name,

        @Size(max = 120, message = "Filial deve ter no máximo 120 caracteres")
        String branchName,

        @Size(max = 255, message = "Endereço deve ter no máximo 255 caracteres")
        String address
) {}

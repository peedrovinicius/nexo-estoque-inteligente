package br.com.nexoestoque.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "Nome do fornecedor é obrigatório")
        @Size(max = 160, message = "Nome deve ter no máximo 160 caracteres")
        String name,

        @Size(max = 32, message = "Documento deve ter no máximo 32 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9./-]*$", message = "Documento contém caracteres inválidos")
        String taxId,

        @Size(max = 120, message = "Contato deve ter no máximo 120 caracteres")
        String contactName,

        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "E-mail deve ter no máximo 160 caracteres")
        String email,

        @Size(max = 40, message = "Telefone deve ter no máximo 40 caracteres")
        String phone,

        @Min(value = 0, message = "Prazo do fornecedor não pode ser negativo")
        int leadTimeDays
) {}

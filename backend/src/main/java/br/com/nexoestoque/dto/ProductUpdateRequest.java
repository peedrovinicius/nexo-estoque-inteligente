package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "SKU é obrigatório")
        @Size(max = 50, message = "SKU deve ter no máximo 50 caracteres")
        String sku,

        @Size(max = 32, message = "Código de barras deve ter no máximo 32 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Código de barras contém caracteres inválidos")
        String barcode,

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 160, message = "Nome deve ter no máximo 160 caracteres")
        String name,

        @NotBlank(message = "Categoria é obrigatória")
        @Size(max = 100, message = "Categoria deve ter no máximo 100 caracteres")
        String category,

        @DecimalMin(value = "0.00", message = "Custo não pode ser negativo")
        BigDecimal costPrice,

        @DecimalMin(value = "0.00", message = "Preço de venda não pode ser negativo")
        BigDecimal salePrice,

        @DecimalMin(value = "0.000", message = "Estoque mínimo não pode ser negativo")
        BigDecimal minimumStock
) {}

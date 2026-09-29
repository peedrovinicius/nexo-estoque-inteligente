package br.com.nexoestoque.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderCreateRequest(
        @NotNull(message = "Fornecedor é obrigatório")
        @Positive(message = "Fornecedor inválido")
        Long supplierId,

        LocalDate expectedAt,

        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
        String notes,

        @NotEmpty(message = "Inclua ao menos um item")
        List<@Valid PurchaseOrderItemRequest> items
) {}

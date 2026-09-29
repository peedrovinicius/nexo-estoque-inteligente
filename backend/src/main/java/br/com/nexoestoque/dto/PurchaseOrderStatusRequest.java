package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PurchaseOrderStatusRequest(
        @NotBlank(message = "Status é obrigatório")
        @Pattern(
                regexp = "^(DRAFT|SENT|PARTIALLY_RECEIVED|RECEIVED|CANCELLED)$",
                message = "Status de pedido inválido"
        )
        String status
) {}

package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReceiptVarianceRequest(
        @NotNull(message = "Pedido é obrigatório")
        @Positive(message = "Pedido inválido")
        Long purchaseOrderId,

        @NotNull(message = "Item do pedido é obrigatório")
        @Positive(message = "Item do pedido inválido")
        Long purchaseOrderItemId,

        @Positive(message = "Recebimento inválido")
        Long purchaseReceiptId,

        @NotBlank(message = "Tipo de divergência é obrigatório")
        String varianceType,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantity,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason
) {}

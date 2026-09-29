package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseReceiptRequest(
        @NotNull(message = "Item do pedido é obrigatório")
        @Positive(message = "Item do pedido inválido")
        Long purchaseOrderItemId,

        @NotNull(message = "Local de recebimento é obrigatório")
        @Positive(message = "Local de recebimento inválido")
        Long locationId,

        @NotBlank(message = "Lote é obrigatório")
        @Size(max = 80, message = "Lote deve ter no máximo 80 caracteres")
        String lotCode,

        @FutureOrPresent(message = "A validade não pode estar no passado")
        LocalDate expiresAt,

        @NotNull(message = "Quantidade recebida é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantity,

        @NotBlank(message = "Chave de idempotência é obrigatória")
        @Size(min = 8, max = 64, message = "Chave de idempotência deve ter entre 8 e 64 caracteres")
        String idempotencyKey
) {}

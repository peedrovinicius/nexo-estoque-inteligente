package br.com.nexoestoque.model;

import java.math.BigDecimal;

public record PurchaseOrderItem(
        Long id,
        Long purchaseOrderId,
        Long productId,
        String sku,
        String productName,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal receivedQuantity
) {}

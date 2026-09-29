package br.com.nexoestoque.model;

import java.util.List;

public record PurchaseOrderDetails(
        PurchaseOrderSummary order,
        List<PurchaseOrderItem> items
) {}

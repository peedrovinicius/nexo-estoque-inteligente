package br.com.nexoestoque.model;

import java.math.BigDecimal;

public record Product(
        Long id,
        String sku,
        String barcode,
        String name,
        String category,
        BigDecimal costPrice,
        BigDecimal salePrice,
        BigDecimal currentStock,
        BigDecimal minimumStock,
        boolean active
) {}

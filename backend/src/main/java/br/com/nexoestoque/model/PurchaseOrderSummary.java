package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurchaseOrderSummary(
        Long id,
        Long supplierId,
        String supplierName,
        String status,
        String source,
        String ruleVersion,
        String createdBy,
        LocalDate expectedAt,
        String notes,
        int itemCount,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

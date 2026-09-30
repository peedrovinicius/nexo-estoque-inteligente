package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockBatch(
        Long id,
        Long productId,
        Long locationId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String branchName,
        String locationCode,
        String aisle,
        String shelf,
        String binCode,
        String sku,
        String productName,
        String lotCode,
        LocalDate expiresAt,
        BigDecimal quantity,
        BigDecimal unitCost,
        String qualityStatus,
        String qualityReason,
        String qualityUpdatedBy,
        LocalDateTime qualityUpdatedAt,
        LocalDateTime receivedAt,
        Integer daysToExpiry,
        String expiryStatus,
        Integer fefoPosition
) {}

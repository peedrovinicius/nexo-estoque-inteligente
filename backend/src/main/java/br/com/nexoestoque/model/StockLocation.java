package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record StockLocation(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String branchName,
        String warehouseAddress,
        String code,
        String aisle,
        String shelf,
        String binCode,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

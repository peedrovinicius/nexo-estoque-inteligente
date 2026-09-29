package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record Warehouse(
        Long id,
        String code,
        String name,
        String branchName,
        String address,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record Supplier(
        Long id,
        String name,
        String taxId,
        String contactName,
        String email,
        String phone,
        int leadTimeDays,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

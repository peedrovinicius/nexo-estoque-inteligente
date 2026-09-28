package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record BlindInventorySession(
        Long id,
        String name,
        String status,
        LocalDateTime startedAt,
        LocalDateTime closedAt,
        int countedItems,
        int divergentItems
) {}

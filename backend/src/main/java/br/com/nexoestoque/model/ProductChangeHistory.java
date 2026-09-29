package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record ProductChangeHistory(
        Long id,
        Long productId,
        String actionType,
        String actorUsername,
        String beforeSnapshot,
        String afterSnapshot,
        LocalDateTime createdAt
) {}

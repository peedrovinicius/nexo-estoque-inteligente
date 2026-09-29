package br.com.nexoestoque.model;

import java.time.LocalDateTime;

public record DecisionAuditEntry(
        Long id,
        Long productId,
        String decisionType,
        String inputSnapshot,
        String outputSnapshot,
        String ruleVersion,
        String actorUsername,
        LocalDateTime createdAt
) {}

package br.com.nexoestoque.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OperationalGovernance {
    private OperationalGovernance() {}

    public record ReplenishmentPolicy(
            long productId,
            String sku,
            String productName,
            boolean enabled,
            int targetCoverageDays,
            BigDecimal safetyStockMultiplier,
            BigDecimal minimumOrderQuantity,
            BigDecimal orderMultiple,
            Long preferredSupplierId,
            String preferredSupplierName,
            String updatedBy,
            LocalDateTime updatedAt
    ) {}

    public record OperationalException(
            long id,
            long productId,
            String sku,
            String productName,
            String exceptionType,
            String reason,
            String status,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            String createdBy,
            String cancelledBy,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt
    ) {}

    public record CycleCountSuggestion(
            long productId,
            String sku,
            String productName,
            String category,
            String abcClass,
            BigDecimal stockValue,
            LocalDateTime lastCountedAt,
            BigDecimal lastDifferenceQuantity,
            int frequencyDays,
            Integer daysSinceLastCount,
            Integer daysOverdue,
            String priority,
            boolean paused
    ) {}

    public record GovernedAction(
            String key,
            String type,
            String severity,
            String title,
            String description,
            String value,
            String actionTarget,
            String status,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            LocalDateTime acknowledgedAt,
            String acknowledgedBy,
            String acknowledgementNote,
            Long ageHours,
            Long slaHours,
            boolean slaBreached
    ) {}

    public record GovernanceSummary(
            int openActions,
            int acknowledgedActions,
            int slaBreaches,
            int activeExceptions,
            int dueCycleCounts,
            List<GovernedAction> actions
    ) {}
}

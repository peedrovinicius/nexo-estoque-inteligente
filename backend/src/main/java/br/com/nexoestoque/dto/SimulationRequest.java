package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SimulationRequest(
        @NotBlank String productName,
        @DecimalMin("0.0") BigDecimal currentStock,
        @DecimalMin("0.01") BigDecimal averageDailyDemand,
        @Min(0) int supplierLeadTimeDays,
        @DecimalMin("0.0") BigDecimal demandVariationPercent,
        @Min(0) int supplierDelayDays,
        @DecimalMin("0.0") BigDecimal plannedPurchase,
        @DecimalMin("0.0") BigDecimal unitCost
) {}

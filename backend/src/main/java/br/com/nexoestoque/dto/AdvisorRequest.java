package br.com.nexoestoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AdvisorRequest(
        @NotBlank String productName,
        @NotBlank String riskLevel,
        @Min(0) int coverageDays,
        @DecimalMin("0.0") BigDecimal recommendedPurchase,
        @DecimalMin("0.0") BigDecimal estimatedValueAtRisk,
        @Min(0) int supplierLeadTimeDays,
        @Min(0) int supplierDelayDays
) {}

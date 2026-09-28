package br.com.nexoestoque.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SimulationResult(
        String riskLevel,
        BigDecimal projectedDailyDemand,
        int coverageDays,
        LocalDate estimatedStockoutDate,
        BigDecimal recommendedPurchase,
        BigDecimal estimatedValueAtRisk,
        String explanation
) {}

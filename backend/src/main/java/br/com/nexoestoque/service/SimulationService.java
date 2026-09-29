package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.SimulationRequest;
import br.com.nexoestoque.dto.SimulationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class SimulationService {
    public static final String RULE_VERSION = "simulation-v1.0.0";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public SimulationResult simulate(SimulationRequest request) {
        BigDecimal factor = BigDecimal.ONE.add(
                request.demandVariationPercent().divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal daily = request.averageDailyDemand()
                .multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal available = request.currentStock().add(request.plannedPurchase());

        int coverage = daily.signum() == 0
                ? 0
                : available.divide(daily, 0, RoundingMode.DOWN).intValue();

        int leadTime = request.supplierLeadTimeDays() + request.supplierDelayDays();
        int safetyDays = Math.max(2, (int) Math.ceil(leadTime * 0.35));

        BigDecimal target = daily.multiply(BigDecimal.valueOf(leadTime + safetyDays));
        BigDecimal recommendation = target.subtract(available)
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING);

        String risk = coverage < leadTime
                ? "ALTO"
                : coverage < leadTime + 4 ? "MODERADO" : "BAIXO";

        LocalDate stockout = daily.signum() == 0
                ? null
                : LocalDate.now().plusDays(coverage);

        BigDecimal shortage = daily.multiply(
                BigDecimal.valueOf(Math.max(0, leadTime - coverage)));

        BigDecimal valueAtRisk = shortage.multiply(request.unitCost())
                .setScale(2, RoundingMode.HALF_UP);

        String explanation =
                "A recomendação usa demanda projetada, prazo efetivo do fornecedor e estoque de segurança. " +
                "Os valores são reproduzíveis e independem da camada de IA.";

        return new SimulationResult(
                risk,
                daily,
                coverage,
                stockout,
                recommendation,
                valueAtRisk,
                explanation
        );
    }
}

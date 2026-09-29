package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.SimulationRequest;
import br.com.nexoestoque.dto.SimulationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {

    private final SimulationService service = new SimulationService();

    @Test
    void calculatesHighRiskScenarioDeterministically() {
        SimulationRequest request = new SimulationRequest(
                "Amoxicilina 500 mg",
                new BigDecimal("42"),
                new BigDecimal("10"),
                5,
                new BigDecimal("20"),
                3,
                BigDecimal.ZERO,
                new BigDecimal("18.40")
        );

        SimulationResult result = service.simulate(request);

        assertThat(result.riskLevel()).isEqualTo("ALTO");
        assertThat(result.projectedDailyDemand()).isEqualByComparingTo("12.00");
        assertThat(result.coverageDays()).isEqualTo(3);
        assertThat(result.recommendedPurchase()).isEqualByComparingTo("90");
        assertThat(result.estimatedValueAtRisk()).isEqualByComparingTo("1104.00");
    }
}

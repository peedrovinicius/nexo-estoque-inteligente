package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.SimulationRequest;
import br.com.nexoestoque.dto.SimulationResult;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import br.com.nexoestoque.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationControllerTest {

    @Test
    void recordsSimulationWithRuleVersionAndActor() throws Exception {
        SimulationService service = mock(SimulationService.class);
        DecisionAuditRepository audit = mock(DecisionAuditRepository.class);
        Authentication authentication = mock(Authentication.class);
        SimulationController controller = new SimulationController(service, audit);

        SimulationRequest request = new SimulationRequest(
                "Produto",
                new BigDecimal("10"),
                new BigDecimal("2"),
                3,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                new BigDecimal("5")
        );

        SimulationResult result = new SimulationResult(
                "BAIXO",
                new BigDecimal("2.00"),
                5,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "explicação"
        );

        when(authentication.getName()).thenReturn("demo");
        when(service.simulate(request)).thenReturn(result);

        SimulationResult response = controller.simulate(request, authentication);

        assertThat(response).isSameAs(result);
        verify(audit).record(
                isNull(),
                eq("STOCK_SIMULATION"),
                same(request),
                same(result),
                eq(SimulationService.RULE_VERSION),
                eq("demo")
        );
    }
}

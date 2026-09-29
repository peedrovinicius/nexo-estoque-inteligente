package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.SimulationRequest;
import br.com.nexoestoque.dto.SimulationResult;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import br.com.nexoestoque.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationService service;
    private final DecisionAuditRepository auditRepository;

    public SimulationController(
            SimulationService service,
            DecisionAuditRepository auditRepository
    ) {
        this.service = service;
        this.auditRepository = auditRepository;
    }

    @PostMapping
    public SimulationResult simulate(
            @Valid @RequestBody SimulationRequest request,
            Authentication authentication
    ) throws SQLException {
        SimulationResult result = service.simulate(request);
        auditRepository.record(
                null,
                "STOCK_SIMULATION",
                request,
                result,
                SimulationService.RULE_VERSION,
                authentication == null ? "system" : authentication.getName()
        );
        return result;
    }
}

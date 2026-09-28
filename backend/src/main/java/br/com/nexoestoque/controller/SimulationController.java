package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.SimulationRequest;
import br.com.nexoestoque.dto.SimulationResult;
import br.com.nexoestoque.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping
    public SimulationResult simulate(@Valid @RequestBody SimulationRequest request) {
        return service.simulate(request);
    }
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.AdvisorRequest;
import br.com.nexoestoque.dto.AdvisorResponse;
import br.com.nexoestoque.service.AdvisorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/advisor")
public class AdvisorController {

    private final AdvisorService advisorService;

    public AdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @PostMapping("/explain")
    public AdvisorResponse explain(@Valid @RequestBody AdvisorRequest request) {
        return advisorService.explain(request);
    }
}

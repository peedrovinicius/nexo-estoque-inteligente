package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.DecisionAuditEntry;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit/decisions")
@Validated
public class DecisionAuditController {
    private final DecisionAuditRepository repository;

    public DecisionAuditController(DecisionAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DecisionAuditEntry> recent(
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit
    ) throws SQLException {
        return repository.findRecent(limit);
    }
}

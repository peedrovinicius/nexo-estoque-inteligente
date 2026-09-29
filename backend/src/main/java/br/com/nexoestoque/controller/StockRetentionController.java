package br.com.nexoestoque.controller;

import br.com.nexoestoque.repository.StockRetentionRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/retention")
@Validated
public class StockRetentionController {
    private static final int RETENTION_MONTHS = 24;
    private final StockRetentionRepository repository;

    public StockRetentionController(StockRetentionRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/archive")
    public Map<String, Object> archive(
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int limit,
            Authentication authentication
    ) throws SQLException {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        int archived = repository.archiveBefore(
                cutoff,
                limit,
                authentication == null ? "system" : authentication.getName()
        );

        return Map.of(
                "retentionMonths", RETENTION_MONTHS,
                "cutoff", cutoff,
                "archivedMovements", archived
        );
    }
}

package br.com.nexoestoque.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final DataSource dataSource;

    public SystemController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "nexo-estoque-api",
                "version", "0.7.0",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness() {
        long startedAt = System.nanoTime();
        boolean databaseReady = false;
        String databaseMessage = "unavailable";

        try (Connection connection = dataSource.getConnection()) {
            databaseReady = connection.isValid(2);
            databaseMessage = databaseReady ? "ready" : "invalid";
        } catch (Exception exception) {
            databaseMessage = "connection_failed";
        }

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", databaseReady ? "ready" : "degraded");
        response.put("api", "ready");
        response.put("database", databaseReady ? "ready" : "unavailable");
        response.put("databaseMessage", databaseMessage);
        response.put("databaseLatencyMs", latencyMs);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}

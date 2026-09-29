package br.com.nexoestoque.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

    private final DataSource dataSource;
    private final boolean enabled;
    private final boolean demoSeedEnabled;

    public DatabaseBootstrap(
            DataSource dataSource,
            @Value("${nexo.db.bootstrap:true}") boolean enabled,
            @Value("${nexo.db.demo-seed:false}") boolean demoSeedEnabled
    ) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Database bootstrap disabled");
            return;
        }

        Exception lastError = null;

        for (int attempt = 1; attempt <= 8; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                if (!demoSeedEnabled) {
                    log.info("Flyway owns schema/procedure migrations; demo seed disabled");
                    return;
                }

                executeScript(connection, Path.of("/app/database/seed-demo.sql"));
                log.info("Demo seed checked after Flyway migration on attempt {}", attempt);
                return;
            } catch (Exception exception) {
                lastError = exception;
                log.warn(
                        "Database bootstrap attempt {}/8 failed: {}",
                        attempt,
                        exception.getMessage()
                );

                if (attempt < 8) {
                    try {
                        Thread.sleep(Math.min(1000L * attempt, 5000L));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        log.warn("Database bootstrap interrupted");
                        return;
                    }
                }
            }
        }

        log.error(
                "Database bootstrap could not synchronize after retries: {}",
                lastError == null ? "unknown error" : lastError.getMessage()
        );
    }

    private void executeScript(Connection connection, Path path) throws IOException, SQLException {
        if (!Files.exists(path)) {
            log.warn("Database script not found: {}", path);
            return;
        }

        String script = Files.readString(path, StandardCharsets.UTF_8);
        for (String statement : splitStatements(script)) {
            String normalized = statement.trim();
            if (normalized.isEmpty()) continue;

            String upper = normalized.toUpperCase();
            if (upper.startsWith("CREATE DATABASE ") || upper.startsWith("USE ")) {
                continue;
            }

            try (Statement jdbcStatement = connection.createStatement()) {
                jdbcStatement.execute(normalized);
            }
        }
    }

    private List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = ";";

        for (String rawLine : script.split("\\R")) {
            String line = rawLine.trim();

            if (line.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = line.substring("DELIMITER ".length()).trim();
                continue;
            }

            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }

            current.append(rawLine).append('\n');

            if (line.endsWith(delimiter)) {
                int removeFrom = current.lastIndexOf(delimiter);
                if (removeFrom >= 0) {
                    current.delete(removeFrom, removeFrom + delimiter.length());
                }
                statements.add(current.toString().trim());
                current.setLength(0);
            }
        }

        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }

        return statements;
    }
}

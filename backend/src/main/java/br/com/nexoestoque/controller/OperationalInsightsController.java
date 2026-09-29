package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.OperationalDashboard;
import br.com.nexoestoque.model.OperationalDashboard.CriticalStockItem;
import br.com.nexoestoque.model.OperationalDashboard.ExpiryRiskItem;
import br.com.nexoestoque.model.OperationalDashboard.StockPositionItem;
import br.com.nexoestoque.model.InventoryIntelligence.*;
import br.com.nexoestoque.repository.OperationalInsightsRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationalInsightsController {
    private final OperationalInsightsRepository repository;

    public OperationalInsightsController(OperationalInsightsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/dashboard")
    public OperationalDashboard dashboard() throws SQLException {
        return repository.dashboard();
    }

    @GetMapping("/critical")
    public List<CriticalStockItem> critical(@RequestParam(defaultValue = "100") int limit) throws SQLException {
        return repository.criticalStock(limit);
    }

    @GetMapping("/expiry")
    public List<ExpiryRiskItem> expiry(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.expiryRisk(days, limit);
    }

    @GetMapping("/stock-position")
    public List<StockPositionItem> stockPosition(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1000") int limit
    ) throws SQLException {
        return repository.stockPosition(warehouseId, limit);
    }


    @GetMapping("/abc")
    public List<AbcItem> abc(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.abcAnalysis(category, query, limit);
    }

    @GetMapping("/slow-moving")
    public List<SlowMovingItem> slowMoving(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.slowMoving(days, category, query, limit);
    }

    @GetMapping("/coverage")
    public List<CoverageItem> coverage(
            @RequestParam(defaultValue = "30") int windowDays,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.coverage(windowDays, category, query, limit);
    }

    @GetMapping("/open-purchases")
    public List<OpenPurchaseAgingItem> openPurchases(@RequestParam(defaultValue = "200") int limit) throws SQLException {
        return repository.openPurchaseAging(limit);
    }

    @GetMapping("/expiry-exposure")
    public ExpiryExposureSummary expiryExposure(
            @RequestParam(defaultValue = "90") int horizonDays,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) throws SQLException {
        return repository.expiryExposure(horizonDays, category, query);
    }

    @GetMapping("/capital")
    public List<CapitalBreakdownItem> capital(
            @RequestParam(defaultValue = "category") String dimension,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "100") int limit
    ) throws SQLException {
        try {
            return repository.capitalBreakdown(dimension, category, query, limit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/alerts")
    public List<OperationalAlert> alerts() throws SQLException {
        return repository.operationalAlerts();
    }

    @GetMapping("/alerts/config")
    public AlertSettings alertConfig() throws SQLException {
        return repository.alertSettings();
    }

    @PutMapping("/alerts/config")
    public AlertSettings updateAlertConfig(
            @RequestBody AlertSettings settings,
            Authentication authentication
    ) throws SQLException {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem alterar alertas");
        }
        validateAlertSettings(settings);
        return repository.updateAlertSettings(settings, authentication.getName());
    }

    @GetMapping(value = "/abc.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> abcCsv(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) throws SQLException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("classe;sku;produto;categoria;quantidade;valor_estoque;participacao_percentual;acumulado_percentual\n");
        for (AbcItem item : repository.abcAnalysis(category, query, 10000)) {
            csv.append(cell(item.abcClass())).append(';')
                    .append(cell(item.sku())).append(';')
                    .append(cell(item.productName())).append(';')
                    .append(cell(item.category())).append(';')
                    .append(cell(item.stockQuantity())).append(';')
                    .append(cell(item.stockValue())).append(';')
                    .append(cell(item.participationPercent())).append(';')
                    .append(cell(item.cumulativePercent())).append('\n');
        }
        return csv("nexo-curva-abc.csv", csv.toString());
    }

    @GetMapping(value = "/slow-moving.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> slowMovingCsv(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) throws SQLException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("sku;produto;saldo;valor_estoque;ultima_saida;dias_sem_saida\n");
        for (SlowMovingItem item : repository.slowMoving(days, category, query, 10000)) {
            csv.append(cell(item.sku())).append(';')
                    .append(cell(item.productName())).append(';')
                    .append(cell(item.currentStock())).append(';')
                    .append(cell(item.stockValue())).append(';')
                    .append(cell(item.lastExitAt())).append(';')
                    .append(cell(item.daysSinceLastExit())).append('\n');
        }
        return csv("nexo-sem-giro.csv", csv.toString());
    }

    @GetMapping(value = "/coverage.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> coverageCsv(
            @RequestParam(defaultValue = "30") int windowDays,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) throws SQLException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("sku;produto;saldo;saidas_janela;consumo_medio_dia;cobertura_dias;nivel\n");
        for (CoverageItem item : repository.coverage(windowDays, category, query, 10000)) {
            csv.append(cell(item.sku())).append(';')
                    .append(cell(item.productName())).append(';')
                    .append(cell(item.currentStock())).append(';')
                    .append(cell(item.exitQuantity())).append(';')
                    .append(cell(item.averageDailyConsumption())).append(';')
                    .append(cell(item.coverageDays())).append(';')
                    .append(cell(item.coverageLevel())).append('\n');
        }
        return csv("nexo-cobertura.csv", csv.toString());
    }

    @GetMapping(value = "/stock-position.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> stockPositionCsv(@RequestParam(required = false) Long warehouseId) throws SQLException {
        List<StockPositionItem> items = repository.stockPosition(warehouseId, 10000);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("deposito;posicao;sku;codigo_barras;produto;categoria;lote;validade;dias_para_validade;quantidade;custo_unitario;valor_estoque\n");

        for (StockPositionItem item : items) {
            csv.append(cell(item.warehouseName())).append(';')
                    .append(cell(item.locationCode())).append(';')
                    .append(cell(item.sku())).append(';')
                    .append(cell(item.barcode())).append(';')
                    .append(cell(item.productName())).append(';')
                    .append(cell(item.category())).append(';')
                    .append(cell(item.lotCode())).append(';')
                    .append(cell(item.expiresAt())).append(';')
                    .append(cell(item.daysToExpiry())).append(';')
                    .append(cell(item.quantity())).append(';')
                    .append(cell(item.unitCost())).append(';')
                    .append(cell(item.stockValue())).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexo-posicao-estoque.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    private void validateAlertSettings(AlertSettings settings) {
        if (settings == null
                || settings.expiryWarningDays() < 1 || settings.expiryWarningDays() > 365
                || settings.lowCoverageDays() < 1 || settings.lowCoverageDays() > 365
                || settings.slowMovingDays() < 1 || settings.slowMovingDays() > 3650
                || settings.purchaseOverdueDays() < 1 || settings.purchaseOverdueDays() > 365
                || settings.coverageWindowDays() < 7 || settings.coverageWindowDays() > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração de alertas inválida");
        }
    }

    private ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.OperationalDashboard;
import br.com.nexoestoque.model.OperationalDashboard.CriticalStockItem;
import br.com.nexoestoque.model.OperationalDashboard.ExpiryRiskItem;
import br.com.nexoestoque.model.OperationalDashboard.StockPositionItem;
import br.com.nexoestoque.repository.OperationalInsightsRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                    .append(cell(item.batchId())).append(';')
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

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

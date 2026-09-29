package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.TraceabilityIntelligence.*;
import br.com.nexoestoque.repository.TraceabilityIntelligenceRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traceability")
public class TraceabilityIntelligenceController {
    private final TraceabilityIntelligenceRepository repository;

    public TraceabilityIntelligenceController(TraceabilityIntelligenceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/suppliers")
    public List<SupplierPerformanceItem> suppliers(
            @RequestParam(defaultValue = "100") int limit
    ) throws SQLException {
        return repository.supplierPerformance(limit);
    }

    @GetMapping("/stock-flow")
    public List<StockFlowItem> stockFlow(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.stockFlow(days, category, query, limit);
    }

    @GetMapping("/movements")
    public List<MovementLedgerItem> movements(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "500") int limit
    ) throws SQLException {
        return repository.movementLedger(query, movementType, actor, from, to, limit);
    }

    @GetMapping(value = "/movements.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> movementsCsv(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) throws SQLException {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("origem;id;data;sku;produto;lotes;tipo;quantidade;saldo_anterior;saldo_posterior;motivo;usuario\n");
        for (MovementLedgerItem item : repository.movementLedger(query, movementType, actor, from, to, 5000)) {
            csv.append(cell(item.source())).append(';')
                    .append(cell(item.movementId())).append(';')
                    .append(cell(item.createdAt())).append(';')
                    .append(cell(item.sku())).append(';')
                    .append(cell(item.productName())).append(';')
                    .append(cell(item.lots())).append(';')
                    .append(cell(item.movementType())).append(';')
                    .append(cell(item.quantity())).append(';')
                    .append(cell(item.balanceBefore())).append(';')
                    .append(cell(item.balanceAfter())).append(';')
                    .append(cell(item.reason())).append(';')
                    .append(cell(item.performedBy())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexo-livro-movimentacoes.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    @GetMapping("/lots/{lotCode}")
    public List<LotTraceEvent> lotTrace(
            @PathVariable String lotCode,
            @RequestParam(defaultValue = "500") int limit
    ) throws SQLException {
        return repository.lotTrace(lotCode, limit);
    }

    @GetMapping("/integrity")
    public InventoryIntegrityReport integrity(
            @RequestParam(defaultValue = "200") int issueLimit
    ) throws SQLException {
        return repository.integrityReport(issueLimit);
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

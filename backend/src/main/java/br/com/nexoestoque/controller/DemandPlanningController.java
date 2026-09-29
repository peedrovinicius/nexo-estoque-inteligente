package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.DemandPlanning.PlanningItem;
import br.com.nexoestoque.model.DemandPlanning.PlanningSummary;
import br.com.nexoestoque.repository.DemandPlanningRepository;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/planning")
public class DemandPlanningController {
    private final DemandPlanningRepository repository;

    public DemandPlanningController(DemandPlanningRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<PlanningItem> planning(@RequestParam(defaultValue = "200") int limit) throws SQLException {
        return repository.planning(limit);
    }

    @GetMapping("/summary")
    public PlanningSummary summary() throws SQLException {
        return repository.summary();
    }

    @GetMapping("/rule")
    public Map<String, String> rule() {
        return Map.of(
                "version", DemandPlanningRepository.RULE_VERSION,
                "forecast", "50% média 7d + 30% média 30d + 20% média 90d",
                "safetyStock", "desvio padrão diário 30d × raiz do lead time",
                "target", "demanda prevista para lead time + 14 dias + estoque de segurança"
        );
    }
}

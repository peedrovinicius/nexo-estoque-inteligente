package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.BatchReplenishmentRequest;
import br.com.nexoestoque.dto.PurchaseOrderCreateRequest;
import br.com.nexoestoque.dto.PurchaseOrderItemRequest;
import br.com.nexoestoque.model.ActionCenter.*;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.repository.ActionCenterRepository;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import br.com.nexoestoque.repository.PurchaseOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
public class ActionCenterService {
    private final ActionCenterRepository actionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DecisionAuditRepository auditRepository;

    public ActionCenterService(
            ActionCenterRepository actionRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            DecisionAuditRepository auditRepository
    ) {
        this.actionRepository = actionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.auditRepository = auditRepository;
    }

    public BatchDraftResult createBatchDrafts(
            BatchReplenishmentRequest request,
            String actor
    ) throws SQLException {
        LinkedHashSet<Long> selected = new LinkedHashSet<>(request.productIds());
        if (selected.size() != request.productIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não repita produtos na seleção");
        }

        List<ReplenishmentSuggestion> all = actionRepository.replenishmentSuggestions(
                request.windowDays(),
                request.demandVariationPercent(),
                1000
        );

        Map<Long, ReplenishmentSuggestion> byProduct = new HashMap<>();
        for (ReplenishmentSuggestion suggestion : all) {
            byProduct.put(suggestion.productId(), suggestion);
        }

        List<ReplenishmentSuggestion> chosen = new ArrayList<>();
        for (Long productId : selected) {
            ReplenishmentSuggestion suggestion = byProduct.get(productId);
            if (suggestion == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Produto " + productId + " não possui necessidade de reposição calculada"
                );
            }
            if (suggestion.supplierRequired() || suggestion.supplierId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Produto " + suggestion.productName() + " ainda não possui fornecedor histórico"
                );
            }
            chosen.add(suggestion);
        }

        Map<Long, List<ReplenishmentSuggestion>> grouped = new LinkedHashMap<>();
        for (ReplenishmentSuggestion suggestion : chosen) {
            grouped.computeIfAbsent(suggestion.supplierId(), ignored -> new ArrayList<>()).add(suggestion);
        }

        List<BatchDraftItem> created = new ArrayList<>();
        for (Map.Entry<Long, List<ReplenishmentSuggestion>> entry : grouped.entrySet()) {
            List<ReplenishmentSuggestion> supplierItems = entry.getValue();
            ReplenishmentSuggestion first = supplierItems.getFirst();
            int leadTime = supplierItems.stream()
                    .mapToInt(ReplenishmentSuggestion::supplierLeadTimeDays)
                    .max()
                    .orElse(0);

            List<PurchaseOrderItemRequest> items = supplierItems.stream()
                    .map(item -> new PurchaseOrderItemRequest(
                            item.productId(),
                            item.recommendedQuantity(),
                            item.referenceUnitCost()
                    ))
                    .toList();

            PurchaseOrderDetails order = purchaseOrderRepository.create(
                    new PurchaseOrderCreateRequest(
                            entry.getKey(),
                            LocalDate.now().plusDays(leadTime),
                            "Rascunho em lote gerado pela Central de Ação.",
                            items
                    ),
                    "REPLENISHMENT_RECOMMENDATION",
                    ActionCenterRepository.RULE_VERSION,
                    actor
            );

            created.add(new BatchDraftItem(
                    order.order().id(),
                    entry.getKey(),
                    first.supplierName(),
                    order.order().itemCount(),
                    order.order().totalAmount()
            ));
        }

        BatchDraftResult result = new BatchDraftResult(
                ActionCenterRepository.RULE_VERSION,
                selected.size(),
                created.size(),
                created
        );

        auditRepository.record(
                null,
                "BATCH_REPLENISHMENT_DRAFT",
                request,
                result,
                ActionCenterRepository.RULE_VERSION,
                actor
        );

        return result;
    }
}

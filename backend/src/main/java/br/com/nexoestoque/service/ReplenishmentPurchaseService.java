package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.*;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.model.Supplier;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import br.com.nexoestoque.repository.PurchaseOrderRepository;
import br.com.nexoestoque.repository.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReplenishmentPurchaseService {
    private final ProductProcedureRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SimulationService simulationService;
    private final DecisionAuditRepository auditRepository;

    public ReplenishmentPurchaseService(
            ProductProcedureRepository productRepository,
            SupplierRepository supplierRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            SimulationService simulationService,
            DecisionAuditRepository auditRepository
    ) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.simulationService = simulationService;
        this.auditRepository = auditRepository;
    }

    public ReplenishmentOrderResponse createDraft(
            ReplenishmentOrderRequest request,
            String actor
    ) throws SQLException {
        Product product = productRepository.findById(request.productId());
        if (product == null || !product.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inexistente ou inativo");
        }

        Supplier supplier = supplierRepository.findById(request.supplierId());
        if (supplier == null || !supplier.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fornecedor inexistente ou inativo");
        }

        SimulationRequest simulationRequest = new SimulationRequest(
                product.name(),
                product.currentStock(),
                request.averageDailyDemand(),
                supplier.leadTimeDays(),
                request.demandVariationPercent(),
                request.supplierDelayDays(),
                request.plannedPurchase(),
                product.costPrice()
        );

        SimulationResult result = simulationService.simulate(simulationRequest);
        if (result.recommendedPurchase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O cenário atual não gera necessidade de reposição"
            );
        }

        LocalDate expectedAt = LocalDate.now()
                .plusDays((long) supplier.leadTimeDays() + request.supplierDelayDays());

        PurchaseOrderCreateRequest createRequest = new PurchaseOrderCreateRequest(
                supplier.id(),
                expectedAt,
                "Sugestão gerada pelo motor determinístico de reposição.",
                List.of(new PurchaseOrderItemRequest(
                        product.id(),
                        result.recommendedPurchase(),
                        product.costPrice()
                ))
        );

        PurchaseOrderDetails order = purchaseOrderRepository.create(
                createRequest,
                "REPLENISHMENT_RECOMMENDATION",
                SimulationService.RULE_VERSION,
                actor
        );

        ReplenishmentOrderResponse response = new ReplenishmentOrderResponse(
                order.order().id(),
                result.riskLevel(),
                result.recommendedPurchase(),
                SimulationService.RULE_VERSION
        );

        auditRepository.record(
                product.id(),
                "REPLENISHMENT_ORDER_SUGGESTION",
                request,
                response,
                SimulationService.RULE_VERSION,
                actor
        );

        return response;
    }
}

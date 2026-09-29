package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.ReplenishmentOrderRequest;
import br.com.nexoestoque.dto.ReplenishmentOrderResponse;
import br.com.nexoestoque.dto.SimulationResult;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.model.PurchaseOrderSummary;
import br.com.nexoestoque.model.Supplier;
import br.com.nexoestoque.repository.DecisionAuditRepository;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import br.com.nexoestoque.repository.PurchaseOrderRepository;
import br.com.nexoestoque.repository.SupplierRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReplenishmentPurchaseServiceTest {

    @Test
    void createsDraftOrderFromDeterministicRecommendation() throws Exception {
        ProductProcedureRepository products = mock(ProductProcedureRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
        SimulationService simulation = mock(SimulationService.class);
        DecisionAuditRepository audit = mock(DecisionAuditRepository.class);

        ReplenishmentPurchaseService service = new ReplenishmentPurchaseService(
                products, suppliers, orders, simulation, audit
        );

        Product product = new Product(
                1L, "MED-001", "", "Produto", "Teste",
                new BigDecimal("5.00"), new BigDecimal("8.00"),
                new BigDecimal("10"), new BigDecimal("3"), true
        );
        Supplier supplier = new Supplier(
                2L, "Fornecedor", "123", "Contato", "x@y.com", "9999",
                5, true, LocalDateTime.now(), LocalDateTime.now()
        );
        SimulationResult simulationResult = new SimulationResult(
                "ALTO",
                new BigDecimal("3.00"),
                3,
                LocalDate.now().plusDays(3),
                new BigDecimal("18"),
                new BigDecimal("30.00"),
                "explicação"
        );
        PurchaseOrderSummary summary = new PurchaseOrderSummary(
                10L, 2L, "Fornecedor", "DRAFT", "REPLENISHMENT_RECOMMENDATION",
                SimulationService.RULE_VERSION, "admin",
                LocalDate.now().plusDays(5),
                "Sugestão gerada pelo motor determinístico de reposição.",
                1, new BigDecimal("90.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(products.findById(1L)).thenReturn(product);
        when(suppliers.findById(2L)).thenReturn(supplier);
        when(simulation.simulate(any())).thenReturn(simulationResult);
        when(orders.create(any(), eq("REPLENISHMENT_RECOMMENDATION"), eq(SimulationService.RULE_VERSION), eq("admin")))
                .thenReturn(new PurchaseOrderDetails(summary, List.of()));

        ReplenishmentOrderResponse response = service.createDraft(
                new ReplenishmentOrderRequest(
                        1L, 2L, new BigDecimal("3"),
                        BigDecimal.ZERO, 0, BigDecimal.ZERO
                ),
                "admin"
        );

        assertThat(response.purchaseOrderId()).isEqualTo(10L);
        assertThat(response.recommendedQuantity()).isEqualByComparingTo("18");
        assertThat(response.ruleVersion()).isEqualTo(SimulationService.RULE_VERSION);

        verify(audit).record(
                eq(1L),
                eq("REPLENISHMENT_ORDER_SUGGESTION"),
                any(),
                same(response),
                eq(SimulationService.RULE_VERSION),
                eq("admin")
        );
    }
}

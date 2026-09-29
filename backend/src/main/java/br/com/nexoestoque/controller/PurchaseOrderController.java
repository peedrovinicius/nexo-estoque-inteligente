package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.*;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.model.PurchaseOrderSummary;
import br.com.nexoestoque.model.PurchaseReceiptResult;
import br.com.nexoestoque.repository.PurchaseOrderRepository;
import br.com.nexoestoque.repository.PurchaseReceiptRepository;
import br.com.nexoestoque.service.ReplenishmentPurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Validated
public class PurchaseOrderController {
    private final PurchaseOrderRepository repository;
    private final PurchaseReceiptRepository receiptRepository;
    private final ReplenishmentPurchaseService replenishmentService;

    public PurchaseOrderController(
            PurchaseOrderRepository repository,
            PurchaseReceiptRepository receiptRepository,
            ReplenishmentPurchaseService replenishmentService
    ) {
        this.repository = repository;
        this.receiptRepository = receiptRepository;
        this.replenishmentService = replenishmentService;
    }

    @GetMapping
    public List<PurchaseOrderSummary> list(@RequestParam(required = false) String status) throws SQLException {
        return repository.findAll(status);
    }

    @GetMapping("/{id}")
    public PurchaseOrderDetails get(@PathVariable @Min(1) long id) throws SQLException {
        PurchaseOrderDetails order = repository.findById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido de compra não encontrado");
        }
        return order;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderDetails create(
            @Valid @RequestBody PurchaseOrderCreateRequest request,
            Authentication authentication
    ) throws SQLException {
        return repository.create(
                request,
                "MANUAL",
                null,
                actor(authentication)
        );
    }

    @PatchMapping("/{id}/status")
    public PurchaseOrderDetails updateStatus(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody PurchaseOrderStatusRequest request
    ) throws SQLException {
        PurchaseOrderDetails order = repository.updateStatus(id, request.status());
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido de compra não encontrado");
        }
        return order;
    }

    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReceiptResult receive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody PurchaseReceiptRequest request,
            Authentication authentication
    ) throws SQLException {
        return receiptRepository.receive(id, request, actor(authentication));
    }

    @PostMapping("/from-recommendation")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplenishmentOrderResponse fromRecommendation(
            @Valid @RequestBody ReplenishmentOrderRequest request,
            Authentication authentication
    ) throws SQLException {
        return replenishmentService.createDraft(request, actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}

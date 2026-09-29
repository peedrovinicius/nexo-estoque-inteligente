package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ApprovalDecisionRequest;
import br.com.nexoestoque.dto.BatchReplenishmentRequest;
import br.com.nexoestoque.dto.StockReservationRequest;
import br.com.nexoestoque.model.ActionCenter.*;
import br.com.nexoestoque.repository.ActionCenterRepository;
import br.com.nexoestoque.service.ActionCenterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/action-center")
public class ActionCenterController {
    private final ActionCenterRepository repository;
    private final ActionCenterService service;

    public ActionCenterController(ActionCenterRepository repository, ActionCenterService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping("/replenishment")
    public List<ReplenishmentSuggestion> replenishment(
            @RequestParam(defaultValue = "30") int windowDays,
            @RequestParam(defaultValue = "0") BigDecimal demandVariationPercent,
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.replenishmentSuggestions(windowDays, demandVariationPercent, limit);
    }

    @PostMapping("/replenishment/batch-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchDraftResult createBatchDrafts(
            @Valid @RequestBody BatchReplenishmentRequest request,
            Authentication authentication
    ) throws SQLException {
        return service.createBatchDrafts(request, actor(authentication));
    }

    @GetMapping("/purchase-approvals")
    public List<PurchaseApprovalItem> approvals() throws SQLException {
        return repository.approvalQueue();
    }

    @PostMapping("/purchase-approvals/{orderId}/request")
    public PurchaseApprovalItem requestApproval(
            @PathVariable long orderId,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.requestApproval(orderId, actor(authentication)));
    }

    @PostMapping("/purchase-approvals/{orderId}/approve")
    public PurchaseApprovalItem approve(
            @PathVariable long orderId,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication
    ) throws SQLException {
        requireAdmin(authentication);
        String reason = request == null ? null : request.reason();
        return translate(() -> repository.decideApproval(orderId, true, actor(authentication), reason));
    }

    @PostMapping("/purchase-approvals/{orderId}/reject")
    public PurchaseApprovalItem reject(
            @PathVariable long orderId,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication
    ) throws SQLException {
        requireAdmin(authentication);
        String reason = request == null ? null : request.reason();
        return translate(() -> repository.decideApproval(orderId, false, actor(authentication), reason));
    }

    @GetMapping("/reservations")
    public List<StockReservation> reservations(
            @RequestParam(required = false) String status
    ) throws SQLException {
        return repository.reservations(status);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public StockReservation createReservation(
            @Valid @RequestBody StockReservationRequest request,
            Authentication authentication
    ) throws SQLException {
        if (request.expiresAt() != null && !request.expiresAt().isAfter(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A validade da reserva deve estar no futuro");
        }
        return translate(() -> repository.createReservation(request, actor(authentication)));
    }

    @PostMapping("/reservations/{id}/cancel")
    public StockReservation cancelReservation(
            @PathVariable long id,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.cancelReservation(id, actor(authentication)));
    }

    @GetMapping("/daily-actions")
    public DailyActionQueue dailyActions() throws SQLException {
        return repository.dailyActions();
    }

    private void requireAdmin(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem decidir aprovações");
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private <T> T translate(SqlOperation<T> operation) throws SQLException {
        try {
            return operation.run();
        } catch (SQLException exception) {
            if ("45000".equals(exception.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
            }
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.StockMovementRequest;
import br.com.nexoestoque.model.StockMovement;
import br.com.nexoestoque.repository.StockMovementProcedureRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/stock/movements")
public class StockMovementController {
    private static final Set<String> ALLOWED_TYPES = Set.of("ENTRY", "EXIT", "ADJUSTMENT", "RETURN");
    private final StockMovementProcedureRepository repository;

    public StockMovementController(StockMovementProcedureRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<StockMovement> recent(@RequestParam(defaultValue = "12") int limit) throws SQLException {
        return repository.findRecent(limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovement create(@Valid @RequestBody StockMovementRequest request, Authentication authentication) throws SQLException {
        validate(request);
        try {
            return repository.create(new StockMovementRequest(
                    request.productId(),
                    request.movementType().toUpperCase(Locale.ROOT),
                    request.quantity(),
                    request.reason() == null ? "" : request.reason().trim(),
                    request.idempotencyKey()
            ), authentication == null ? "system" : authentication.getName());
        } catch (SQLException exception) {
            if ("45000".equals(exception.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
            }
            if ("23000".equals(exception.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência já utilizada", exception);
            }
            throw exception;
        }
    }

    private void validate(StockMovementRequest request) {
        if (request.productId() == null || request.productId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inválido");
        }
        if (request.movementType() == null ||
                !ALLOWED_TYPES.contains(request.movementType().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de movimentação inválido");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade não pode ser zero");
        }

        if (request.idempotencyKey() == null || request.idempotencyKey().trim().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma chave de idempotência válida");
        }

        String type = request.movementType().toUpperCase(Locale.ROOT);
        if (!"ADJUSTMENT".equals(type) && request.quantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero");
        }
    }
}

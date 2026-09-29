package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.BatchAdjustmentRequest;
import br.com.nexoestoque.dto.BatchEntryRequest;
import br.com.nexoestoque.dto.BatchReturnRequest;
import br.com.nexoestoque.dto.FefoExitRequest;
import br.com.nexoestoque.dto.FefoPreviewRequest;
import br.com.nexoestoque.dto.FefoPreviewResponse;
import br.com.nexoestoque.model.MovementAllocation;
import br.com.nexoestoque.model.StockBatch;
import br.com.nexoestoque.model.StockOperationResult;
import br.com.nexoestoque.repository.StockBatchProcedureRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/batches")
public class StockBatchController {
    private final StockBatchProcedureRepository repository;

    public StockBatchController(StockBatchProcedureRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<StockBatch> list(@RequestParam(required = false) Long productId) throws SQLException {
        return repository.findBatches(productId);
    }

    @PostMapping("/entry")
    @ResponseStatus(HttpStatus.CREATED)
    public StockOperationResult entry(@Valid @RequestBody BatchEntryRequest request, Authentication authentication) throws SQLException {
        validateProductAndQuantity(request.productId(), request.quantity());

        if (request.lotCode() == null || request.lotCode().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o lote da entrada");
        }

        try {
            return repository.entry(new BatchEntryRequest(
                    request.productId(),
                    request.lotCode().trim(),
                    request.expiresAt(),
                    request.quantity(),
                    request.unitCost() == null ? BigDecimal.ZERO : request.unitCost(),
                    request.reason() == null ? "" : request.reason().trim(),
                    requireIdempotencyKey(request.idempotencyKey())
            ), actor(authentication));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @PostMapping("/exit-fefo/preview")
    public FefoPreviewResponse previewExit(@Valid @RequestBody FefoPreviewRequest request) throws SQLException {
        validateProductAndQuantity(request.productId(), request.quantity());

        try {
            return repository.previewExit(request.productId(), request.quantity());
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @PostMapping("/exit-fefo")
    @ResponseStatus(HttpStatus.CREATED)
    public StockOperationResult exitFefo(@Valid @RequestBody FefoExitRequest request, Authentication authentication) throws SQLException {
        validateProductAndQuantity(request.productId(), request.quantity());

        try {
            return repository.exitFefo(new FefoExitRequest(
                    request.productId(),
                    request.quantity(),
                    request.reason() == null ? "" : request.reason().trim(),
                    requireIdempotencyKey(request.idempotencyKey())
            ), actor(authentication));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }


    @PostMapping("/return")
    @ResponseStatus(HttpStatus.CREATED)
    public StockOperationResult returnToBatch(@Valid @RequestBody BatchReturnRequest request, Authentication authentication) throws SQLException {
        validateProductAndQuantity(request.productId(), request.quantity());

        if (request.batchId() == null || request.batchId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lote inválido");
        }

        try {
            return repository.returnToBatch(new BatchReturnRequest(
                    request.productId(),
                    request.batchId(),
                    request.quantity(),
                    request.reason() == null ? "" : request.reason().trim(),
                    requireIdempotencyKey(request.idempotencyKey())
            ), actor(authentication));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @PostMapping("/adjustment")
    @ResponseStatus(HttpStatus.CREATED)
    public StockOperationResult adjustBatch(@Valid @RequestBody BatchAdjustmentRequest request, Authentication authentication) throws SQLException {
        if (request.productId() == null || request.productId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inválido");
        }
        if (request.batchId() == null || request.batchId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lote inválido");
        }
        if (request.quantityDelta() == null || request.quantityDelta().compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ajuste deve ser diferente de zero");
        }

        try {
            return repository.adjustBatch(new BatchAdjustmentRequest(
                    request.productId(),
                    request.batchId(),
                    request.quantityDelta(),
                    request.reason() == null ? "" : request.reason().trim(),
                    requireIdempotencyKey(request.idempotencyKey())
            ), actor(authentication));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @GetMapping("/movements/{movementId}/allocations")
    public List<MovementAllocation> allocations(@PathVariable long movementId) throws SQLException {
        return repository.findAllocations(movementId);
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private void validateProductAndQuantity(Long productId, BigDecimal quantity) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inválido");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero");
        }
    }

    private String requireIdempotencyKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.length() < 8 || normalized.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma chave de idempotência válida");
        }
        return normalized;
    }

    private ResponseStatusException translate(SQLException exception) throws SQLException {
        if ("45000".equals(exception.getSQLState())) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        if ("23000".equals(exception.getSQLState())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência já utilizada", exception);
        }
        throw exception;
    }
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.BatchEntryRequest;
import br.com.nexoestoque.dto.FefoExitRequest;
import br.com.nexoestoque.model.MovementAllocation;
import br.com.nexoestoque.model.StockBatch;
import br.com.nexoestoque.model.StockOperationResult;
import br.com.nexoestoque.repository.StockBatchProcedureRepository;
import org.springframework.http.HttpStatus;
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
    public StockOperationResult entry(@RequestBody BatchEntryRequest request) throws SQLException {
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
                    request.reason() == null ? "" : request.reason().trim()
            ));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @PostMapping("/exit-fefo")
    @ResponseStatus(HttpStatus.CREATED)
    public StockOperationResult exitFefo(@RequestBody FefoExitRequest request) throws SQLException {
        validateProductAndQuantity(request.productId(), request.quantity());

        try {
            return repository.exitFefo(new FefoExitRequest(
                    request.productId(),
                    request.quantity(),
                    request.reason() == null ? "" : request.reason().trim()
            ));
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @GetMapping("/movements/{movementId}/allocations")
    public List<MovementAllocation> allocations(@PathVariable long movementId) throws SQLException {
        return repository.findAllocations(movementId);
    }

    private void validateProductAndQuantity(Long productId, BigDecimal quantity) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inválido");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero");
        }
    }

    private ResponseStatusException translate(SQLException exception) throws SQLException {
        if ("45000".equals(exception.getSQLState())) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        throw exception;
    }
}

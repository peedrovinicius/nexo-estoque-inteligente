package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.BlindInventoryCountRequest;
import br.com.nexoestoque.dto.BlindInventorySessionRequest;
import br.com.nexoestoque.model.BlindInventoryItem;
import br.com.nexoestoque.model.BlindInventorySession;
import br.com.nexoestoque.repository.BlindInventoryProcedureRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/blind")
public class BlindInventoryController {
    private final BlindInventoryProcedureRepository repository;

    public BlindInventoryController(BlindInventoryProcedureRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<BlindInventorySession> list() throws SQLException {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlindInventorySession create(@Valid @RequestBody BlindInventorySessionRequest request) throws SQLException {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome para o inventário");
        }
        return repository.create(name);
    }

    @GetMapping("/{sessionId}/items")
    public List<BlindInventoryItem> items(@PathVariable long sessionId) throws SQLException {
        return repository.findItems(sessionId);
    }

    @PutMapping("/{sessionId}/counts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void count(
            @PathVariable long sessionId,
            @Valid @RequestBody BlindInventoryCountRequest request
    ) throws SQLException {
        if (request.productId() == null || request.productId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inválido");
        }
        if (request.countedQuantity() == null || request.countedQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A contagem não pode ser negativa");
        }
        try {
            repository.count(sessionId, request.productId(), request.countedQuantity());
        } catch (SQLException exception) {
            if ("45000".equals(exception.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
            }
            throw exception;
        }
    }

    @PostMapping("/{sessionId}/close")
    public BlindInventorySession close(@PathVariable long sessionId) throws SQLException {
        try {
            return repository.close(sessionId);
        } catch (SQLException exception) {
            if ("45000".equals(exception.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
            }
            throw exception;
        }
    }
}

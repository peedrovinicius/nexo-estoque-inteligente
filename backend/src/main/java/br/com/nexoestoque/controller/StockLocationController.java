package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ActiveStatusRequest;
import br.com.nexoestoque.dto.StockLocationRequest;
import br.com.nexoestoque.model.StockLocation;
import br.com.nexoestoque.repository.WarehouseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/locations")
@Validated
public class StockLocationController {
    private final WarehouseRepository repository;

    public StockLocationController(WarehouseRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<StockLocation> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Boolean active
    ) throws SQLException {
        return repository.findLocations(warehouseId, active);
    }

    @GetMapping("/{id}")
    public StockLocation get(@PathVariable @Min(1) long id) throws SQLException {
        StockLocation location = repository.findLocation(id);
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Posição de estoque não encontrada");
        }
        return location;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockLocation create(@Valid @RequestBody StockLocationRequest request) throws SQLException {
        return repository.createLocation(request);
    }

    @PutMapping("/{id}")
    public StockLocation update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody StockLocationRequest request
    ) throws SQLException {
        StockLocation location = repository.updateLocation(id, request);
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Posição de estoque não encontrada");
        }
        return location;
    }

    @PatchMapping("/{id}/active")
    public StockLocation setActive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ActiveStatusRequest request
    ) throws SQLException {
        StockLocation location = repository.setLocationActive(id, request.active());
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Posição de estoque não encontrada");
        }
        return location;
    }
}

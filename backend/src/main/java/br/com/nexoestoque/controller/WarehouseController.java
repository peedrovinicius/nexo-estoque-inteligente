package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ActiveStatusRequest;
import br.com.nexoestoque.dto.WarehouseRequest;
import br.com.nexoestoque.model.Warehouse;
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
@RequestMapping("/api/v1/warehouses")
@Validated
public class WarehouseController {
    private final WarehouseRepository repository;

    public WarehouseController(WarehouseRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Warehouse> list(@RequestParam(required = false) Boolean active) throws SQLException {
        return repository.findWarehouses(active);
    }

    @GetMapping("/{id}")
    public Warehouse get(@PathVariable @Min(1) long id) throws SQLException {
        Warehouse warehouse = repository.findWarehouse(id);
        if (warehouse == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Depósito não encontrado");
        }
        return warehouse;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Warehouse create(@Valid @RequestBody WarehouseRequest request) throws SQLException {
        return repository.createWarehouse(request);
    }

    @PutMapping("/{id}")
    public Warehouse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody WarehouseRequest request
    ) throws SQLException {
        Warehouse warehouse = repository.updateWarehouse(id, request);
        if (warehouse == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Depósito não encontrado");
        }
        return warehouse;
    }

    @PatchMapping("/{id}/active")
    public Warehouse setActive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ActiveStatusRequest request
    ) throws SQLException {
        Warehouse warehouse = repository.setWarehouseActive(id, request.active());
        if (warehouse == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Depósito não encontrado");
        }
        return warehouse;
    }
}

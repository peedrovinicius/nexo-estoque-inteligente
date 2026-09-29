package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.SupplierRequest;
import br.com.nexoestoque.dto.SupplierStatusRequest;
import br.com.nexoestoque.model.Supplier;
import br.com.nexoestoque.repository.SupplierRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
public class SupplierController {
    private final SupplierRepository repository;

    public SupplierController(SupplierRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Supplier> list(@RequestParam(required = false) Boolean active) throws SQLException {
        return repository.findAll(active);
    }

    @GetMapping("/{id}")
    public Supplier get(@PathVariable @Min(1) long id) throws SQLException {
        Supplier supplier = repository.findById(id);
        if (supplier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fornecedor não encontrado");
        }
        return supplier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier create(@Valid @RequestBody SupplierRequest request) throws SQLException {
        return repository.create(request);
    }

    @PutMapping("/{id}")
    public Supplier update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody SupplierRequest request
    ) throws SQLException {
        Supplier supplier = repository.update(id, request);
        if (supplier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fornecedor não encontrado");
        }
        return supplier;
    }

    @PatchMapping("/{id}/active")
    public Supplier setActive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody SupplierStatusRequest request
    ) throws SQLException {
        Supplier supplier = repository.setActive(id, request.active());
        if (supplier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fornecedor não encontrado");
        }
        return supplier;
    }
}

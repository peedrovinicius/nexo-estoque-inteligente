package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ProductCreateRequest;
import br.com.nexoestoque.dto.ProductPage;
import br.com.nexoestoque.dto.ProductStatusRequest;
import br.com.nexoestoque.dto.ProductUpdateRequest;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {
    private final ProductProcedureRepository repository;

    public ProductController(ProductProcedureRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ProductPage list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) throws SQLException {
        return repository.search(page, size, q, category, active, sort, direction);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable @Min(1) long id) throws SQLException {
        Product product = repository.findById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return product;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody ProductCreateRequest product) throws SQLException {
        BigDecimal openingStock = product.currentStock() == null
                ? BigDecimal.ZERO
                : product.currentStock();

        if (openingStock.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Produto novo deve iniciar com estoque zero; registre a entrada por lote"
            );
        }

        return Map.of("id", repository.create(product));
    }

    @PutMapping("/{id}")
    public Product update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ProductUpdateRequest product
    ) throws SQLException {
        Product updated = repository.update(id, product);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return updated;
    }

    @PatchMapping("/{id}/active")
    public Product setActive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ProductStatusRequest request
    ) throws SQLException {
        Product updated = repository.setActive(id, request.active());
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return updated;
    }
}

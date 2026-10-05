package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ProductCreateRequest;
import br.com.nexoestoque.dto.ProductPage;
import br.com.nexoestoque.dto.ProductStatusRequest;
import br.com.nexoestoque.dto.ProductUpdateRequest;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.ProductChangeHistory;
import br.com.nexoestoque.repository.ProductHistoryRepository;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {
    private final ProductProcedureRepository repository;
    private final ProductHistoryRepository historyRepository;

    public ProductController(
            ProductProcedureRepository repository,
            ProductHistoryRepository historyRepository
    ) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    @GetMapping
    public ProductPage list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) @Size(max = 160) String q,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "name")
            @Pattern(regexp = "^(name|sku|category|stock|minimumStock|createdAt)$") String sort,
            @RequestParam(defaultValue = "asc")
            @Pattern(regexp = "^(?i:asc|desc)$") String direction
    ) throws SQLException {
        return repository.search(page, size, q, category, active, sort, direction);
    }

    @GetMapping("/barcode/{barcode}")
    public Product getByBarcode(
            @PathVariable
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$")
            String barcode
    ) throws SQLException {
        Product product = repository.findByBarcode(barcode);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado para o código de barras");
        }
        return product;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable @Min(1) long id) throws SQLException {
        Product product = repository.findById(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return product;
    }

    @GetMapping("/{id}/history")
    public List<ProductChangeHistory> history(
            @PathVariable @Min(1) long id,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit
    ) throws SQLException {
        if (repository.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }
        return historyRepository.findByProduct(id, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(
            @Valid @RequestBody ProductCreateRequest product,
            Authentication authentication
    ) throws SQLException {
        BigDecimal openingStock = product.currentStock() == null
                ? BigDecimal.ZERO
                : product.currentStock();

        if (openingStock.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Produto novo deve iniciar com estoque zero; registre a entrada por lote"
            );
        }

        long id = repository.create(product);
        Product created = repository.findById(id);
        historyRepository.record(id, "CREATE", actor(authentication), null, created);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Product update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ProductUpdateRequest product,
            Authentication authentication
    ) throws SQLException {
        Product before = repository.findById(id);
        if (before == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }

        Product updated = repository.update(id, product);
        historyRepository.record(id, "UPDATE", actor(authentication), before, updated);
        return updated;
    }

    @PatchMapping("/{id}/active")
    public Product setActive(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ProductStatusRequest request,
            Authentication authentication
    ) throws SQLException {
        Product before = repository.findById(id);
        if (before == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado");
        }

        Product updated = repository.setActive(id, request.active());
        historyRepository.record(
                id,
                request.active() ? "REACTIVATE" : "DEACTIVATE",
                actor(authentication),
                before,
                updated
        );
        return updated;
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}

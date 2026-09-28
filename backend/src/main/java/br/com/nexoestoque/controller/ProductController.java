package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductProcedureRepository repository;

    public ProductController(ProductProcedureRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Product> list() throws SQLException {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@RequestBody Product product) throws SQLException {
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
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.StockTransferRequest;
import br.com.nexoestoque.model.StockTransferResult;
import br.com.nexoestoque.repository.StockTransferRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/api/v1/stock/transfers")
public class StockTransferController {
    private final StockTransferRepository repository;

    public StockTransferController(StockTransferRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockTransferResult transfer(
            @Valid @RequestBody StockTransferRequest request,
            Authentication authentication
    ) throws SQLException {
        return repository.transfer(
                request,
                authentication == null ? "system" : authentication.getName()
        );
    }
}

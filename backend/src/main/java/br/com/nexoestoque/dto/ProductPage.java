package br.com.nexoestoque.dto;

import br.com.nexoestoque.model.Product;

import java.util.List;

public record ProductPage(
        List<Product> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}

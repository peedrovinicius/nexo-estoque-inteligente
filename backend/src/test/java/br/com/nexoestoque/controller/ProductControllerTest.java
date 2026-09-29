package br.com.nexoestoque.controller;

import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductControllerTest {

    @Test
    void rejectsProductWithNonZeroOpeningStock() {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductController controller = new ProductController(repository);

        Product product = new Product(
                null,
                "TEST-001",
                "",
                "Produto teste",
                "Teste",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true
        );

        assertThatThrownBy(() -> controller.create(product))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(repository);
    }
}

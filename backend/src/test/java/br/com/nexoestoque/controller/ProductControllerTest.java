package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ProductCreateRequest;
import br.com.nexoestoque.dto.ProductStatusRequest;
import br.com.nexoestoque.dto.ProductUpdateRequest;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductControllerTest {

    @Test
    void rejectsProductWithNonZeroOpeningStock() {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductController controller = new ProductController(repository);

        ProductCreateRequest product = new ProductCreateRequest(
                "TEST-001",
                "",
                "Produto teste",
                "Teste",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO
        );

        assertThatThrownBy(() -> controller.create(product))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(repository);
    }

    @Test
    void updatesProductMetadataWithoutDirectStockField() throws Exception {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductController controller = new ProductController(repository);
        Product expected = new Product(
                1L, "TEST-001", "", "Produto atualizado", "Teste",
                BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(7),
                BigDecimal.valueOf(3), true
        );
        when(repository.update(eq(1L), any(ProductUpdateRequest.class))).thenReturn(expected);

        Product result = controller.update(1L, new ProductUpdateRequest(
                "TEST-001", "", "Produto atualizado", "Teste",
                BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(3)
        ));

        assertThat(result.currentStock()).isEqualByComparingTo("7");
        verify(repository).update(eq(1L), any(ProductUpdateRequest.class));
    }

    @Test
    void delegatesControlledStatusChangeToRepository() throws Exception {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductController controller = new ProductController(repository);
        Product inactive = new Product(
                2L, "ZERO-001", "", "Produto zerado", "Teste",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false
        );
        when(repository.setActive(2L, false)).thenReturn(inactive);

        Product result = controller.setActive(2L, new ProductStatusRequest(false));

        assertThat(result.active()).isFalse();
        verify(repository).setActive(2L, false);
    }
}

package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ProductCreateRequest;
import br.com.nexoestoque.dto.ProductStatusRequest;
import br.com.nexoestoque.dto.ProductUpdateRequest;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.repository.ProductHistoryRepository;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
        ProductHistoryRepository history = mock(ProductHistoryRepository.class);
        ProductController controller = new ProductController(repository, history);

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

        assertThatThrownBy(() -> controller.create(product, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(repository, history);
    }

    @Test
    void updatesProductMetadataAndRecordsActorWithoutChangingStock() throws Exception {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductHistoryRepository history = mock(ProductHistoryRepository.class);
        Authentication authentication = mock(Authentication.class);
        ProductController controller = new ProductController(repository, history);

        Product before = new Product(
                1L, "TEST-001", "", "Produto original", "Teste",
                BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(7),
                BigDecimal.valueOf(3), true
        );
        Product expected = new Product(
                1L, "TEST-001", "", "Produto atualizado", "Teste",
                BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(7),
                BigDecimal.valueOf(3), true
        );

        when(authentication.getName()).thenReturn("admin");
        when(repository.findById(1L)).thenReturn(before);
        when(repository.update(eq(1L), any(ProductUpdateRequest.class))).thenReturn(expected);

        Product result = controller.update(1L, new ProductUpdateRequest(
                "TEST-001", "", "Produto atualizado", "Teste",
                BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(3)
        ), authentication);

        assertThat(result.currentStock()).isEqualByComparingTo("7");
        verify(history).record(1L, "UPDATE", "admin", before, expected);
    }

    @Test
    void recordsControlledDeactivationWithActor() throws Exception {
        ProductProcedureRepository repository = mock(ProductProcedureRepository.class);
        ProductHistoryRepository history = mock(ProductHistoryRepository.class);
        Authentication authentication = mock(Authentication.class);
        ProductController controller = new ProductController(repository, history);

        Product before = new Product(
                2L, "ZERO-001", "", "Produto zerado", "Teste",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true
        );
        Product inactive = new Product(
                2L, "ZERO-001", "", "Produto zerado", "Teste",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false
        );

        when(authentication.getName()).thenReturn("operador");
        when(repository.findById(2L)).thenReturn(before);
        when(repository.setActive(2L, false)).thenReturn(inactive);

        Product result = controller.setActive(2L, new ProductStatusRequest(false), authentication);

        assertThat(result.active()).isFalse();
        verify(history).record(2L, "DEACTIVATE", "operador", before, inactive);
    }
}

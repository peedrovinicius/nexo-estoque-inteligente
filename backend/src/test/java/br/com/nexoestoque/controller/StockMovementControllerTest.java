package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.StockMovementRequest;
import br.com.nexoestoque.repository.StockMovementProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StockMovementControllerTest {

    @Test
    void rejectsWriteWithoutIdempotencyKey() {
        StockMovementProcedureRepository repository = mock(StockMovementProcedureRepository.class);
        StockMovementController controller = new StockMovementController(repository);

        StockMovementRequest request = new StockMovementRequest(
                1L,
                "ENTRY",
                BigDecimal.ONE,
                "teste",
                ""
        );

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(repository);
    }
}

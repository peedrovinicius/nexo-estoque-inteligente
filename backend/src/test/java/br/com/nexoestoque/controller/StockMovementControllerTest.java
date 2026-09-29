package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.StockMovementRequest;
import br.com.nexoestoque.model.StockMovement;
import br.com.nexoestoque.repository.StockMovementProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        assertThatThrownBy(() -> controller.create(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException response = (ResponseStatusException) error;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(repository);
    }

    @Test
    void forwardsAuthenticatedActorToRepository() throws Exception {
        StockMovementProcedureRepository repository = mock(StockMovementProcedureRepository.class);
        Authentication authentication = mock(Authentication.class);
        StockMovementController controller = new StockMovementController(repository);

        StockMovementRequest request = new StockMovementRequest(
                1L,
                "ADJUSTMENT",
                BigDecimal.ONE,
                "teste",
                "movement-actor-001"
        );
        StockMovement expected = new StockMovement(
                10L, 1L, "Produto", "ADJUSTMENT", BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ONE, "teste", "admin", LocalDateTime.now()
        );

        when(authentication.getName()).thenReturn("admin");
        when(repository.create(any(StockMovementRequest.class), eq("admin"))).thenReturn(expected);

        StockMovement result = controller.create(request, authentication);

        assertThat(result.performedBy()).isEqualTo("admin");
        verify(repository).create(any(StockMovementRequest.class), eq("admin"));
    }
}

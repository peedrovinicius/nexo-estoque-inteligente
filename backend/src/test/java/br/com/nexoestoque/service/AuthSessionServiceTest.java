package br.com.nexoestoque.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(
            User.withUsername("demo").password(encoder.encode("test-password")).roles("VIEWER").build());
    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-10-04T13:00:00Z");

    private AuthSessionService service() {
        when(clock.instant()).thenReturn(start);
        return new AuthSessionService(users, encoder, 1800, clock);
    }

    @Test
    void issuesRandomSessionsWithoutReturningCredentialsAndRevokesOnlySelectedToken() {
        var service = service();
        var first = service.login("demo", "test-password", "127.0.0.1");
        var second = service.login("demo", "test-password", "127.0.0.1");
        assertThat(first.token()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.token());
        assertThat(first.role()).isEqualTo("VIEWER");
        assertThat(service.authenticate(first.token())).isPresent();
        service.revoke(first.token());
        assertThat(service.authenticate(first.token())).isEmpty();
        assertThat(service.authenticate(second.token())).isPresent();
    }

    @Test
    void rejectsExpiredMalformedAndDeletedUserSessions() {
        var service = service();
        var session = service.login("demo", "test-password", "127.0.0.1");
        assertThat(service.authenticate("Basic secret")).isEmpty();
        when(clock.instant()).thenReturn(start.plusSeconds(1800));
        assertThat(service.authenticate(session.token())).isEmpty();
        when(clock.instant()).thenReturn(start);
        session = service.login("demo", "test-password", "127.0.0.1");
        users.deleteUser("demo");
        assertThat(service.authenticate(session.token())).isEmpty();
    }

    @Test
    void blocksAfterFiveInvalidAttemptsAndRecoversAfterWindow() {
        var service = service();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("demo", "wrong", "127.0.0.1"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login("DEMO", "test-password", "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429));
        when(clock.instant()).thenReturn(start.plusSeconds(901));
        assertThat(service.login("demo", "test-password", "127.0.0.1").token()).isNotBlank();
    }

    @Test
    void publicDemoIssuesViewerSessionsWithoutPasswordAndLimitsCreation() {
        var service = service();
        for (int i = 0; i < 5; i++) {
            var session = service.demo("demo", "127.0.0.1");
            assertThat(session.role()).isEqualTo("VIEWER");
            assertThat(service.authenticate(session.token())).isPresent();
        }
        assertThatThrownBy(() -> service.demo("demo", "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429));
    }

    @Test
    void unknownAndKnownUsersReceiveSameCredentialFailure() {
        var service = service();
        assertThatThrownBy(() -> service.login("absent", "wrong", "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Usuário ou senha inválidos");
        assertThatThrownBy(() -> service.login("demo", "wrong", "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Usuário ou senha inválidos");
    }
}

package br.com.nexoestoque.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance sessions. Only token hashes and usernames are retained server-side. */
@Service
public class AuthSessionService {
    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);
    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOGIN_WINDOW_SECONDS = 900;
    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final long lifetimeSeconds;
    private final String dummyPasswordHash;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attempts = new HashMap<>();

    @Autowired
    public AuthSessionService(UserDetailsService users, PasswordEncoder encoder,
            @Value("${nexo.auth.session-seconds:1800}") long lifetimeSeconds) {
        this(users, encoder, lifetimeSeconds, Clock.systemUTC());
    }

    AuthSessionService(UserDetailsService users, PasswordEncoder encoder,
            long lifetimeSeconds, Clock clock) {
        if (lifetimeSeconds < 60 || lifetimeSeconds > 3600) {
            throw new IllegalArgumentException("Session lifetime must be between 60 and 3600 seconds");
        }
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.lifetimeSeconds = lifetimeSeconds;
        this.dummyPasswordHash = encoder.encode("unavailable-account-verification");
    }

    public SessionResponse login(String username, String password, String address) {
        String key = digest(String.valueOf(address) + "\n" + username.toLowerCase(Locale.ROOT));
        String fingerprint = shortHash(key);
        try {
            consumeAttempt(key);
        } catch (ResponseStatusException exception) {
            log.warn("security_event=login_rate_limited identity={}", fingerprint);
            throw exception;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            encoder.matches("invalid", dummyPasswordHash);
            log.warn("security_event=login_failure identity={} reason=credential_rejected", fingerprint);
            throw new BadCredentialsException("Usuário ou senha inválidos");
        }
        UserDetails user;
        try {
            user = users.loadUserByUsername(username);
        } catch (UsernameNotFoundException exception) {
            encoder.matches(password, dummyPasswordHash);
            log.warn("security_event=login_failure identity={} reason=credential_rejected", fingerprint);
            throw new BadCredentialsException("Usuário ou senha inválidos");
        }
        if (!encoder.matches(password, user.getPassword()) || !user.isEnabled()
                || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            log.warn("security_event=login_failure identity={} reason=credential_rejected", fingerprint);
            throw new BadCredentialsException("Usuário ou senha inválidos");
        }
        SessionResponse response = issue(user);
        synchronized (attempts) { attempts.remove(key); }
        log.info("security_event=login_success identity={} role={}", fingerprint, response.role());
        return response;
    }

    public SessionResponse demo(String username, String address) {
        String key = digest(String.valueOf(address) + "\n__public_demo__");
        String fingerprint = shortHash(key);
        try {
            consumeAttempt(key);
        } catch (ResponseStatusException exception) {
            log.warn("security_event=demo_rate_limited identity={}", fingerprint);
            throw exception;
        }

        UserDetails user;
        try {
            user = users.loadUserByUsername(username);
        } catch (UsernameNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demonstração indisponível");
        }

        boolean viewer = user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_VIEWER".equals(authority.getAuthority()));

        if (!viewer || !user.isEnabled() || !user.isAccountNonLocked()
                || !user.isAccountNonExpired() || !user.isCredentialsNonExpired()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demonstração indisponível");
        }

        SessionResponse response = issue(user);
        log.info("security_event=demo_session_issued identity={}", fingerprint);
        return response;
    }

    private void consumeAttempt(String key) {
        Instant now = clock.instant();
        synchronized (attempts) {
            attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            Attempts previous = attempts.get(key);
            if ((previous == null && attempts.size() >= MAX_ENTRIES)
                    || (previous != null && previous.count() >= MAX_ATTEMPTS)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde para tentar novamente");
            }
            attempts.put(key, previous == null
                    ? new Attempts(1, now.plusSeconds(LOGIN_WINDOW_SECONDS))
                    : new Attempts(previous.count() + 1, previous.expiresAt()));
        }
    }

    private synchronized SessionResponse issue(UserDetails user) {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (sessions.size() >= MAX_ENTRIES) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível iniciar a sessão");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plusSeconds(lifetimeSeconds);
        sessions.put(digest(token), new Session(user.getUsername(), expiresAt));
        String role = user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("VIEWER");
        return new SessionResponse(user.getUsername(), role, token, expiresAt);
    }

    public Optional<UserDetails> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        String hash = digest(token);
        Session session = sessions.get(hash);
        if (session == null) return Optional.empty();
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(hash);
            return Optional.empty();
        }
        try {
            UserDetails user = users.loadUserByUsername(session.username());
            if (user.isEnabled() && user.isAccountNonLocked() && user.isAccountNonExpired()
                    && user.isCredentialsNonExpired()) return Optional.of(user);
        } catch (UsernameNotFoundException ignored) {
            // Removed users lose access immediately.
        }
        sessions.remove(hash);
        return Optional.empty();
    }

    public void revoke(String token) {
        if (token != null) {
            String hash = digest(token);
            sessions.remove(hash);
            log.info("security_event=session_revoked token={}", shortHash(hash));
        }
    }

    private static String shortHash(String value) {
        String hash = digest(value);
        return hash.substring(0, Math.min(12, hash.length()));
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Session(String username, Instant expiresAt) {}
    private record Attempts(int count, Instant expiresAt) {}
    public record SessionResponse(String username, String role, String token, Instant expiresAt) {}
}

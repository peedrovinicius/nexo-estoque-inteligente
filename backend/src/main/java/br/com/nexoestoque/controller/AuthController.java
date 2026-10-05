package br.com.nexoestoque.controller;

import br.com.nexoestoque.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthSessionService sessions;
    private final String demoUser;

    public AuthController(
            AuthSessionService sessions,
            @Value("${nexo.auth.demo-user:demo}") String demoUser
    ) {
        this.sessions = sessions;
        this.demoUser = demoUser;
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<AuthSessionService.SessionResponse> login(
            @Valid @RequestBody LoginRequest input, HttpServletRequest request) {
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(sessions.login(input.username(), input.password(), request.getRemoteAddr()));
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos");
        }
    }

    @PostMapping("/demo")
    public ResponseEntity<AuthSessionService.SessionResponse> demo(HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(sessions.demo(demoUser, request.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        if (authorization.startsWith("Bearer ")) sessions.revoke(authorization.substring(7));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password) {}
}

package br.com.nexoestoque.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void restrictsCorsToConfiguredOriginsWithoutCredentials() {
        SecurityConfig config = new SecurityConfig(
                "admin", "admin-secret",
                "operador", "operator-secret",
                "demo"
        );

        var source = config.corsConfigurationSource(
                "https://nexo.example.com,http://localhost:5173"
        );
        var cors = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/products")
        );

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://nexo.example.com", "http://localhost:5173");
        assertThat(cors.getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders())
                .contains("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(cors.getAllowCredentials()).isFalse();
    }

    @Test
    void exposesAdminOperatorAndViewerRolesWhenConfigured() {
        SecurityConfig config = new SecurityConfig(
                "admin", "admin-secret",
                "operador", "operator-secret",
                "demo"
        );

        UserDetailsService users = config.userDetailsService(config.passwordEncoder());

        UserDetails admin = users.loadUserByUsername("admin");
        UserDetails operator = users.loadUserByUsername("operador");
        UserDetails viewer = users.loadUserByUsername("demo");

        assertThat(admin.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(operator.getAuthorities()).extracting("authority").containsExactly("ROLE_OPERATOR");
        assertThat(viewer.getAuthorities()).extracting("authority").containsExactly("ROLE_VIEWER");
    }
}

package br.com.nexoestoque.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void exposesAdminOperatorAndViewerRolesWhenConfigured() {
        SecurityConfig config = new SecurityConfig(
                "admin", "admin-secret",
                "operador", "operator-secret",
                "demo", "demo-secret"
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

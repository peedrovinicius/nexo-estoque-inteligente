package br.com.nexoestoque.config;

import br.com.nexoestoque.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {
    private final String adminUser;
    private final String adminPassword;
    private final String operatorUser;
    private final String operatorPassword;
    private final String demoUser;
    private final String demoPassword;

    public SecurityConfig(
            @Value("${nexo.auth.admin-user:admin}") String adminUser,
            @Value("${nexo.auth.admin-password:}") String adminPassword,
            @Value("${nexo.auth.operator-user:operador}") String operatorUser,
            @Value("${nexo.auth.operator-password:}") String operatorPassword,
            @Value("${nexo.auth.demo-user:demo}") String demoUser,
            @Value("${nexo.auth.demo-password:Nexo@2026}") String demoPassword
    ) {
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.operatorUser = operatorUser;
        this.operatorPassword = operatorPassword;
        this.demoUser = demoUser;
        this.demoPassword = demoPassword;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        users.add(User.withUsername(demoUser)
                .password(encoder.encode(demoPassword))
                .roles("VIEWER")
                .build());

        if (adminPassword != null && !adminPassword.isBlank()) {
            users.add(User.withUsername(adminUser)
                    .password(encoder.encode(adminPassword))
                    .roles("ADMIN")
                    .build());
        }

        if (operatorPassword != null && !operatorPassword.isBlank()) {
            users.add(User.withUsername(operatorUser)
                    .password(encoder.encode(operatorPassword))
                    .roles("OPERATOR")
                    .build());
        }

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/api/v1/system/health", "/api/v1/system/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/simulations",
                                "/api/v1/advisor/**",
                                "/api/v1/stock/batches/exit-fefo/preview"
                        ).hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                                    OffsetDateTime.now(),
                                    401,
                                    "Unauthorized",
                                    "AUTHENTICATION_REQUIRED",
                                    "Autenticação obrigatória",
                                    request.getRequestURI(),
                                    Map.of()
                            ));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                                    OffsetDateTime.now(),
                                    403,
                                    "Forbidden",
                                    "ACCESS_DENIED",
                                    "Você não tem permissão para executar esta operação",
                                    request.getRequestURI(),
                                    Map.of()
                            ));
                        })
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}

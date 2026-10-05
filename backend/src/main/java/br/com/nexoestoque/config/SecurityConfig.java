package br.com.nexoestoque.config;

import br.com.nexoestoque.dto.ApiError;
import br.com.nexoestoque.service.AuthSessionService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    private final String adminUser;
    private final String adminPassword;
    private final String operatorUser;
    private final String operatorPassword;
    private final String demoUser;

    public SecurityConfig(
            @Value("${nexo.auth.admin-user:admin}") String adminUser,
            @Value("${nexo.auth.admin-password:}") String adminPassword,
            @Value("${nexo.auth.operator-user:operador}") String operatorUser,
            @Value("${nexo.auth.operator-password:}") String operatorPassword,
            @Value("${nexo.auth.demo-user:demo}") String demoUser
    ) {
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.operatorUser = operatorUser;
        this.operatorPassword = operatorPassword;
        this.demoUser = demoUser;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        users.add(User.withUsername(demoUser)
                .password(encoder.encode(UUID.randomUUID().toString()))
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
    CorsConfigurationSource corsConfigurationSource(
            @Value("${nexo.cors.allowed-origins}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalArgumentException("At least one CORS origin must be configured");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            AuthSessionService sessions,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new SessionAuthenticationFilter(sessions), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new ApiRateLimitFilter(), SessionAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/api/v1/system/health", "/api/v1/system/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/demo").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit/**").hasAnyRole("ADMIN", "OPERATOR")
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
                .build();
    }
}

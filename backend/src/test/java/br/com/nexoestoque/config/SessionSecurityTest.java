package br.com.nexoestoque.config;

import br.com.nexoestoque.controller.AuthController;
import br.com.nexoestoque.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ObjectMapper mapper;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "nexo.auth.admin-password=admin-test-password");
        context.register(TestConfig.class);
        context.refresh();
        mapper = context.getBean(ObjectMapper.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    private String login(String username, String password) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain(password).doesNotContain("authorization");
        return mapper.readTree(result).get("token").asText();
    }

    @Test void rejectsBasicAndAcceptsBearerWithRoleRestrictions() throws Exception {
        mvc.perform(get("/api/v1/session-test").header("Authorization", "Basic ZGVtbzpOZXhvQDIwMjY="))
                .andExpect(status().isUnauthorized());
        String viewer = login("demo", "Nexo@2026");
        mvc.perform(get("/api/v1/session-test").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/session-test").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
        String admin = login("admin", "admin-test-password");
        mvc.perform(post("/api/v1/session-test").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }
    @Test void logoutRevokesTokenAndMalformedTokensDoNotAuthenticate() throws Exception {
        String token = login("demo", "Nexo@2026");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/session-test").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/session-test").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsMissingCredentialsAndLimitsFailedLogins() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class, ProbeController.class})
    static class TestConfig {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean AuthSessionService sessions(UserDetailsService users, PasswordEncoder encoder) {
            return new AuthSessionService(users, encoder, 1800);
        }
    }
    @RestController static class ProbeController {
        @GetMapping("/api/v1/session-test") String read() { return "ok"; }
        @PostMapping("/api/v1/session-test") String write() { return "ok"; }
    }
}

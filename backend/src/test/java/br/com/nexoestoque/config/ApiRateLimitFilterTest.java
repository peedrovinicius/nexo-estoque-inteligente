package br.com.nexoestoque.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimitFilterTest {
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-05T01:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void blocksWritesAfterConfiguredLimit() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(3, 2, clock);

        assertThat(run(filter, "POST", "/api/v1/products").getStatus()).isEqualTo(200);
        assertThat(run(filter, "POST", "/api/v1/products").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = run(filter, "POST", "/api/v1/products");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void keepsReadAndWriteBudgetsSeparate() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(1, 1, clock);

        assertThat(run(filter, "GET", "/api/v1/products").getStatus()).isEqualTo(200);
        assertThat(run(filter, "POST", "/api/v1/products").getStatus()).isEqualTo(200);
        assertThat(run(filter, "GET", "/api/v1/products").getStatus()).isEqualTo(429);
        assertThat(run(filter, "POST", "/api/v1/products").getStatus()).isEqualTo(429);
    }

    @Test
    void excludesAuthHealthAndPreflight() {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(1, 1, clock);

        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/demo"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/system/readiness"))).isTrue();
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/products"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/products"))).isFalse();
    }

    private MockHttpServletResponse run(ApiRateLimitFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = request(method, path);
        request.addHeader("Authorization", "Bearer " + "a".repeat(43));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}

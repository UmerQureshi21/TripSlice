package com.umerqureshicodes.tripslice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private int status(RateLimitFilter filter, String ip, String realIpHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/generate-token");
        request.setServletPath("/generate-token");
        request.setRemoteAddr(ip);
        if (realIpHeader != null) {
            request.addHeader("X-Real-IP", realIpHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void blocksLoginAfterTenAttemptsFromOneIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("", false);
        for (int i = 0; i < 10; i++) {
            assertThat(status(filter, "1.2.3.4", null)).isEqualTo(200);
        }
        assertThat(status(filter, "1.2.3.4", null)).isEqualTo(429);
        // A different client is unaffected
        assertThat(status(filter, "5.6.7.8", null)).isEqualTo(200);
    }

    @Test
    void usesTheConfiguredProxyHeaderForTheClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("X-Real-IP", false);
        // Every request arrives from the proxy's address, but the real clients differ
        for (int i = 0; i < 10; i++) {
            assertThat(status(filter, "10.0.0.1", "9.9.9.9")).isEqualTo(200);
        }
        assertThat(status(filter, "10.0.0.1", "9.9.9.9")).isEqualTo(429);
        assertThat(status(filter, "10.0.0.1", "8.8.8.8")).isEqualTo(200);
    }

    @Test
    void perUserPhaseIgnoresAnonymousRequests() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("", true);
        for (int i = 0; i < 50; i++) {
            assertThat(status(filter, "1.2.3.4", null)).isEqualTo(200);
        }
    }
}

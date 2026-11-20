package com.umerqureshicodes.tripslice.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Cloudflare is replaced by a mock server
class TurnstileServiceTest {

    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private record Setup(TurnstileService service, MockRestServiceServer server) {}

    private Setup withSecret(String secret) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new TurnstileService(secret, builder.build()), server);
    }

    @Test
    void isOffWithoutASecret() {
        TurnstileService service = new TurnstileService("");
        assertThat(service.isEnabled()).isFalse();
        assertThat(service.verify(null, null)).isTrue();
        assertThat(new TurnstileService(null).isEnabled()).isFalse();
    }

    @Test
    void acceptsAValidToken() {
        Setup s = withSecret("secret");
        s.server().expect(requestTo(URL))
                .andExpect(content().formDataContains(java.util.Map.of("secret", "secret", "response", "tok", "remoteip", "1.2.3.4")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        assertThat(s.service().verify("tok", "1.2.3.4")).isTrue();
        s.server().verify();
    }

    @Test
    void rejectsMissingOrInvalidTokens() {
        Setup s = withSecret("secret");
        assertThat(s.service().verify(" ", "1.2.3.4")).isFalse();
        assertThat(s.service().verify(null, null)).isFalse();
        s.server().expect(requestTo(URL)).andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        assertThat(s.service().verify("bad", null)).isFalse();
    }

    @Test
    void failsClosedWhenCloudflareIsDown() {
        Setup s = withSecret("secret");
        s.server().expect(requestTo(URL)).andRespond(withServerError());
        assertThat(s.service().verify("tok", "1.2.3.4")).isFalse();
    }
}

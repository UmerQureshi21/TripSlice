package com.umerqureshicodes.tripslice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Checks the Cloudflare Turnstile token sent with sign up, so scripts can't mass-create accounts
 * to get around the per-user limits. Disabled when TURNSTILE_SECRET is empty (local dev).
 */
@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secret;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public TurnstileService(@Value("${turnstile.secret:}") String secret) {
        this(secret, RestClient.builder().requestFactory(timeouts()).build());
    }

    // For tests, so the Cloudflare call can be pointed at a mock server
    TurnstileService(String secret, RestClient restClient) {
        this.secret = secret == null ? "" : secret.trim();
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        return factory;
    }

    public boolean isEnabled() {
        return !secret.isEmpty();
    }

    public boolean verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null) {
            form.add("remoteip", remoteIp);
        }
        try {
            Map<?, ?> body = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return body != null && Boolean.TRUE.equals(body.get("success"));
        } catch (Exception e) {
            // Fail closed, an outage at Cloudflare pauses sign ups rather than opening them up
            log.warn("Turnstile verification failed: {}", e.getMessage());
            return false;
        }
    }
}

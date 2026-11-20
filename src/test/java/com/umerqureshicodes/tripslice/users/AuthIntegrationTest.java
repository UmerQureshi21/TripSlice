package com.umerqureshicodes.tripslice.users;

import com.umerqureshicodes.tripslice.JWT.JwtUtil;
import com.umerqureshicodes.tripslice.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    JwtUtil jwtUtil;

    private String registerBody(String email, String password, String name) throws Exception {
        return JSON.writeValueAsString(Map.of("username", email, "password", password, "displayedName", name));
    }

    @Test
    void registersAndLogsIn() throws Exception {
        String ip = uniqueIp();
        mvc.perform(from(post("/register"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("fresh@test.dev", "longenough1", "Fresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fresh@test.dev"));

        MvcResult login = login("fresh@test.dev", "longenough1", ip);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getHeader("Authorization")).startsWith("Bearer ");
        assertThat(login.getResponse().getHeader("Set-Cookie"))
                .contains("refreshToken=").contains("HttpOnly").contains("Path=/refresh-token");
    }

    @Test
    void refusesDuplicateEmailOrName() throws Exception {
        TestUser user = newUser();
        mvc.perform(from(post("/register"), uniqueIp()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(user.email(), "longenough1", "Someone Else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("That email or display name is already in use."));
        mvc.perform(from(post("/register"), uniqueIp()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("other@test.dev", "longenough1", user.entity().getDisplayedName())))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesSignUpOnTheServer() throws Exception {
        mvc.perform(from(post("/register"), uniqueIp()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("short@test.dev", "short", "Shorty")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be 8 to 72 characters"));
        mvc.perform(from(post("/register"), uniqueIp()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "longenough1", "Valid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a valid email"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        TestUser user = newUser();
        MvcResult result = login(user.email(), "wrong-password", uniqueIp());
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("Invalid credentials");
    }

    @Test
    void protectedRoutesNeedAValidAccessToken() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(get("/user"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.email()));
        mvc.perform(from(get("/user"), user.ip())).andExpect(status().isForbidden());
        mvc.perform(from(get("/user"), user.ip()).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        // A refresh token is not accepted where an access token is expected
        String refresh = jwtUtil.generateToken(user.email(), 5, JwtUtil.TokenType.REFRESH);
        mvc.perform(from(get("/user"), user.ip()).header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshIssuesANewAccessTokenUntilLogout() throws Exception {
        TestUser user = newUser();
        Cookie cookie = refreshCookie(login(user.email(), user.password(), user.ip()));

        mvc.perform(from(post("/refresh-token"), user.ip()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));

        mvc.perform(from(post("/refresh-token/logout"), user.ip()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        // Revoked server side, so a copied cookie is useless after logout
        mvc.perform(from(post("/refresh-token"), user.ip()).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshNeedsAValidRefreshCookie() throws Exception {
        String ip = uniqueIp();
        mvc.perform(from(post("/refresh-token"), ip)).andExpect(status().isForbidden());
        mvc.perform(from(post("/refresh-token"), ip).cookie(new Cookie("other", "x"))).andExpect(status().isForbidden());
        mvc.perform(from(post("/refresh-token"), ip).cookie(new Cookie("refreshToken", "garbage")))
                .andExpect(status().isUnauthorized());
        // Logging out without a cookie, or with an invalid one, still just clears it
        mvc.perform(from(post("/refresh-token/logout"), ip)).andExpect(status().isOk());
        mvc.perform(from(post("/refresh-token/logout"), ip).cookie(new Cookie("refreshToken", "garbage")))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 10; i++) {
            assertThat(login("nobody@test.dev", "whatever1", ip).getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = login("nobody@test.dev", "whatever1", ip);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("300");
    }

    @Test
    void healthIsPublicAndPreflightsPass() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(options("/videos")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        // Other origins get no CORS approval
        mvc.perform(options("/videos")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void localhostRequestsGetANonSecureCookie() {
        assertThat(jwtUtil.buildRefreshCookie("v", 10, true)).contains("SameSite=Lax").doesNotContain("Secure");
        assertThat(jwtUtil.buildRefreshCookie("v", 10, false)).contains("SameSite=None").contains("Secure");
        assertThat(JwtUtil.isLocalhost("127.0.0.1")).isTrue();
        assertThat(JwtUtil.isLocalhost("tripslice.app")).isFalse();
    }
}

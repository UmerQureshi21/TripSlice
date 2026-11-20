package com.umerqureshicodes.tripslice.JWT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umerqureshicodes.tripslice.users.UserRequestDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

//This filer responsible for loggins users in
//So we have created a filter, added it to filter chain
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    // Same lifetime as tokens issued by /refresh-token
    private static final long ACCESS_TOKEN_MINUTES = 15;

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only handle POST /generate-token
        if (!JwtUtil.pathOf(request).equals("/generate-token") ||
                !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Parse JSON body into DTO
            UserRequestDTO userRequestDTO =
                    objectMapper.readValue(request.getReader(), UserRequestDTO.class);

            // Build auth token (username + password), this one uses DaoAuth provider
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            userRequestDTO.username(),
                            userRequestDTO.password()
                    );

            // Perform authentication
            Authentication authResult = authenticationManager.authenticate(authenticationToken);

            if(authResult.isAuthenticated()) {
            String token = jwtUtil.generateToken(authResult.getName(), ACCESS_TOKEN_MINUTES, JwtUtil.TokenType.ACCESS);
                response.setHeader("Authorization", "Bearer " + token) ;

            String refreshToken = jwtUtil.generateToken(authResult.getName(), 7*24*60, JwtUtil.TokenType.REFRESH);

                // Set refresh token in HttpOnly Cookie
            // We can also send it in response body but then client has to store in memory or in local storage
                response.addHeader("Set-Cookie", jwtUtil.buildRefreshCookie(
                        refreshToken,
                        7 * 24 * 60 * 60,
                        JwtUtil.isLocalhost(request.getServerName())
                ));
            }

        } catch (Exception ex) {
            // Never log the request body here, it holds the password
            log.debug("Login failed: {}", ex.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid credentials\"}");
            response.getWriter().flush();
        }
    }
}

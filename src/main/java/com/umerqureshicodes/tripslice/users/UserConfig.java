package com.umerqureshicodes.tripslice.users;

import com.umerqureshicodes.tripslice.JWT.*;
import com.umerqureshicodes.tripslice.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
public class UserConfig {

    private final JwtUtil jwtUtil;

    @Value("${frontend.host}")
    private String frontendUrl;

    @Value("${ratelimit.client-ip-header:}")
    private String clientIpHeader;

    public UserConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Collections.singletonList(frontendUrl));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        // Lets the frontend read the access token from the login and refresh responses
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Inject UserService and PasswordEncoder here instead of as fields
    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserService userService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public JwtAuthenticationProvider jwtAuthenticationProvider(JwtUtil jwtUtil, UserService userService, TokenRevocationService tokenRevocationService) {
        return new JwtAuthenticationProvider(jwtUtil, userService, tokenRevocationService);
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider, JwtAuthenticationProvider jwtAuthenticationProvider) {
        return new ProviderManager(Arrays.asList(daoAuthenticationProvider,jwtAuthenticationProvider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationManager authenticationManager,
                                                   DaoAuthenticationProvider daoAuthenticationProvider,
                                                   TokenRevocationService tokenRevocationService) throws Exception {

        // Authentication filter responsible for login
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(authenticationManager, jwtUtil);

        // Validation filter responsible for checking Jwt in every request
        JwtValidationFilter jwtValidationFilter = new JwtValidationFilter(authenticationManager);

        // Refresh filter for checking Jwt in every request
        JwtRefreshFilter jwtRefreshFilter = new JwtRefreshFilter(authenticationManager, jwtUtil);



        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authenticationProvider(daoAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/gs-guide-websocket/**", "/register", "/generate-token", "/refresh-token", "/refresh-token/logout", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                // Revokes the refresh token and clears its cookie. The URL is under the cookie's path so the browser sends it
                .logout(logout -> logout
                        .logoutUrl("/refresh-token/logout")
                        .addLogoutHandler(new JwtLogoutHandler(jwtUtil, tokenRevocationService))
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_OK))
                )
                // Order: per-IP limits -> login -> token validation -> per-user limits -> refresh.
                // Each position is relative to a filter added earlier, so the calls must stay in this order
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // generate token filter
                .addFilterAfter(jwtValidationFilter, JwtAuthenticationFilter.class) // validate token filter
                .addFilterAfter(new RateLimitFilter(clientIpHeader, true), JwtValidationFilter.class) // per-user limits
                .addFilterAfter(jwtRefreshFilter, RateLimitFilter.class) // refresh token filter
                .addFilterBefore(new RateLimitFilter(clientIpHeader, false), JwtAuthenticationFilter.class); // per-IP limits

        return http.build();
    }
}

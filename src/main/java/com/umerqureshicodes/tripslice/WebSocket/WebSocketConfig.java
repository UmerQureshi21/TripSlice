package com.umerqureshicodes.tripslice.WebSocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthenticationManager authenticationManager;
    private final String frontendHost;

    public WebSocketConfig(AuthenticationManager authenticationManager, @Value("${frontend.host}") String frontendHost) {
        this.authenticationManager = authenticationManager;
        this.frontendHost = frontendHost;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Only per-user queues are used, there is no broadcast topic
        config.enableSimpleBroker("/queue");
        // Clients subscribe to /user/queue/... to only get messages sent to them with convertAndSendToUser
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketAuthInterceptor(authenticationManager));
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/gs-guide-websocket")
                .setAllowedOrigins(frontendHost);
    }

}
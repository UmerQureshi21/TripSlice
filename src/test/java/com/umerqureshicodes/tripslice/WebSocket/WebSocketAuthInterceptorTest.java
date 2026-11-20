package com.umerqureshicodes.tripslice.WebSocket;

import com.umerqureshicodes.tripslice.JWT.JwtAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(manager);
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectNeedsABearerToken() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null), channel))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Basic abc"), channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectWithAValidTokenSetsTheUser() {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken("me@test.dev", null, List.of());
        when(manager.authenticate(any(JwtAuthenticationToken.class))).thenReturn(user);
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good"), channel);
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isEqualTo(user);
    }

    @Test
    void clientsMayOnlySubscribeNotSend() {
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null), channel)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null), channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void progressMessagesCarryContentAndPath() {
        WebSocketServiceMessage message = new WebSocketServiceMessage("done", "https://x");
        assertThat(message.getContent()).isEqualTo("done");
        assertThat(message.getMontagePath()).isEqualTo("https://x");
    }
}

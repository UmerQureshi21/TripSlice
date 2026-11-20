package com.umerqureshicodes.tripslice.common;

import com.umerqureshicodes.tripslice.FFmpeg.FFmpegService;
import com.umerqureshicodes.tripslice.security.TurnstileService;
import com.umerqureshicodes.tripslice.users.UserController;
import com.umerqureshicodes.tripslice.users.UserRequestDTO;
import com.umerqureshicodes.tripslice.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsEachErrorToAStatusAndMessage() {
        var busy = handler.handleBusy(new FFmpegService.EncoderBusyException());
        assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(busy.getBody()).containsKey("error");

        var tooLarge = handler.handleTooLarge(new MaxUploadSizeExceededException(1));
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(tooLarge.getBody().get("error")).isEqualTo("Uploads can be at most 200MB in total.");

        var api = handler.handleApi(new ApiException(HttpStatus.BAD_GATEWAY, "upstream"));
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(new ApiException(HttpStatus.CONFLICT, "x").getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void signUpIsRefusedWhenTheBotCheckFails() {
        UserService users = mock(UserService.class);
        TurnstileService turnstile = mock(TurnstileService.class);
        when(turnstile.verify(eq("bad"), any())).thenReturn(false);
        UserController controller = new UserController(users, turnstile, "X-Real-IP");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "9.9.9.9, 10.0.0.1");

        UserRequestDTO dto = new UserRequestDTO("Name", "password1", "a@test.dev", "bad");
        assertThatThrownBy(() -> controller.register(dto, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Please complete the verification check and try again.");
        verify(turnstile).verify("bad", "9.9.9.9");
        verifyNoInteractions(users);
    }
}

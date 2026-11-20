package com.umerqureshicodes.tripslice.users;

import com.umerqureshicodes.tripslice.common.ApiException;
import com.umerqureshicodes.tripslice.security.TurnstileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

    private final UserService userService;
    private final TurnstileService turnstileService;
    private final String clientIpHeader;

    public UserController(UserService userService, TurnstileService turnstileService,
                          @Value("${ratelimit.client-ip-header:}") String clientIpHeader) {
        this.userService = userService;
        this.turnstileService = turnstileService;
        this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
    }

    @PostMapping("/register")
    public UserResponseDTO register(@Valid @RequestBody UserRequestDTO userRequestDTO, HttpServletRequest request) {
        if (!turnstileService.verify(userRequestDTO.turnstileToken(), clientIp(request))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please complete the verification check and try again.");
        }
        return userService.createUser(userRequestDTO);
    }

    @GetMapping("/user")
    public UserResponseDTO getUser(@AuthenticationPrincipal AppUser appUser){
        return userService.getUser(appUser.getUsername());
    }

    private String clientIp(HttpServletRequest request) {
        if (!clientIpHeader.isEmpty() && request.getHeader(clientIpHeader) != null) {
            return request.getHeader(clientIpHeader).split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

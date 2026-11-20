package com.umerqureshicodes.tripslice.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Used for sign up (validated) and for login (only username and password are read)
public record UserRequestDTO (
        @NotBlank(message = "A display name is required")
        @Size(min = 2, max = 30, message = "Display name must be 2 to 30 characters")
        String displayedName,

        // BCrypt only uses the first 72 bytes, longer passwords are refused rather than silently cut
        @NotBlank(message = "A password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password,

        @NotBlank(message = "An email is required")
        @Email(message = "Please enter a valid email")
        @Size(max = 254, message = "Email is too long")
        String username,

        // Cloudflare Turnstile token, only checked on sign up when TURNSTILE_SECRET is set
        String turnstileToken
) {
}

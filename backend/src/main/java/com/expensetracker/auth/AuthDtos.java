package com.expensetracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                    message = "muss 3–50 Zeichen lang sein (Buchstaben, Ziffern, . _ -)")
            String username,
            // BCrypt only considers the first 72 bytes
            @NotBlank @Size(min = 8, max = 72, message = "muss 8–72 Zeichen lang sein") String password,
            /** Optional, for password reset */
            @Email @Size(max = 254) String email) {
    }

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record PasswordResetConfirmation(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72, message = "muss 8–72 Zeichen lang sein") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "muss 8–72 Zeichen lang sein") String newPassword) {
    }

    /** Blank or null {@code email} removes the address. */
    public record ChangeEmailRequest(@Email @Size(max = 254) String email) {
    }

    public record AccountResponse(String username, String email, Instant createdAt) {
    }

    /** Tokens travel only in httpOnly cookies; the body tells the client who is logged in. */
    public record SessionResponse(String username, Instant accessTokenExpiresAt) {
    }

    public record UserResponse(Long id, String username) {
    }

    public record AuthConfig(boolean registrationEnabled, boolean passwordResetEnabled) {
    }
}

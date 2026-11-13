package com.expensetracker.auth;

import com.expensetracker.mail.MailService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Password reset via a single-use link sent by mail. Like refresh tokens, only a SHA-256 hash of the token is
 * stored. Requests never reveal whether an address belongs to an account.
 */
@Service
public class PasswordResetService {

    static final String INVALID = "Der Link ist ungültig oder abgelaufen. Bitte fordere einen neuen an.";

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final MailService mail;
    private final Duration validity;
    private final String baseUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AppUserRepository users, PasswordResetTokenRepository tokens,
                                RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder,
                                LoginRateLimiter rateLimiter, MailService mail, AuthProperties properties,
                                @Value("${app.base-url}") String baseUrl, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.mail = mail;
        this.validity = properties.passwordResetValidity();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    public boolean enabled() {
        return mail.enabled();
    }

    /** Mails a reset link if an account uses {@code email}; otherwise does nothing, observably the same. */
    @Transactional
    public void requestReset(String email, String clientIp) {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Passwort-Reset ist nicht eingerichtet");
        }
        // Counted like failed logins for the address, so nobody can flood an inbox with reset mails
        String limitKey = "reset:" + email.trim();
        rateLimiter.checkAllowed(clientIp, limitKey);
        rateLimiter.recordAttempt(clientIp);
        rateLimiter.recordFailure(clientIp, limitKey);

        users.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            tokens.deleteAllOfUser(user.getId());
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Instant now = clock.instant();
            tokens.save(new PasswordResetToken(user.getId(), RefreshTokenService.hash(token), now, now.plus(validity)));
            mail.send(user.getEmail(), "Passwort zurücksetzen", text(user, token));
        });
    }

    /** Sets a new password and ends all logins of the account. */
    @Transactional
    public void resetPassword(String token, String newPassword, String clientIp) {
        rateLimiter.checkAllowed(clientIp, null);
        rateLimiter.recordAttempt(clientIp);
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokens.findByTokenHash(RefreshTokenService.hash(token))
                .filter(t -> t.usableAt(now))
                .orElseThrow(() -> new IllegalArgumentException(INVALID));
        AppUser user = users.findById(resetToken.getUserId()).orElseThrow(() -> new IllegalArgumentException(INVALID));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        refreshTokens.revokeAll(user.getId());
    }

    private String text(AppUser user, String token) {
        long minutes = validity.toMinutes();
        return """
                Hallo %s,

                für dein Konto beim Ausgaben-Tracker wurde ein neues Passwort angefordert.
                Über diesen Link kannst du es in den nächsten %d Minuten festlegen:

                %s/reset-password?token=%s

                Wenn du das nicht warst, kannst du diese Mail ignorieren. Dein Passwort bleibt dann unverändert.
                """.formatted(user.getUsername(), minutes, baseUrl, token);
    }
}

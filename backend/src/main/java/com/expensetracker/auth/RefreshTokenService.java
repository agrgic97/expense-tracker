package com.expensetracker.auth;

import com.expensetracker.common.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens. Only a SHA-256 hash is stored, so a database leak does not expose
 * usable tokens. Presenting an already rotated token outside a short grace period is treated as theft
 * and revokes every token of that login.
 */
@Service
public class RefreshTokenService {

    /** Tolerates parallel refreshes (e.g. two browser tabs) that raced with the same cookie. */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID = "Sitzung abgelaufen. Bitte erneut anmelden.";

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    /** {@code newToken} is null when a recently rotated token was presented within the grace period. */
    public record Rotation(Long userId, IssuedRefreshToken newToken) {
    }

    private final RefreshTokenRepository repository;
    private final Duration validity;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.validity = properties.refreshTokenValidity();
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken create(Long userId) {
        repository.deleteExpired(userId, clock.instant());
        return issue(userId, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String presented) {
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHash(hash(presented))
                .orElseThrow(() -> new UnauthorizedException(INVALID));
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException(INVALID);
        }
        if (token.getRotatedAt() != null) {
            if (token.getRotatedAt().plus(ROTATION_GRACE).isAfter(now)) {
                return new Rotation(token.getUserId(), null);
            }
            log.warn("Wiederverwendetes Refresh-Token für Benutzer {} – alle Tokens dieser Anmeldung werden widerrufen",
                    token.getUserId());
            repository.revokeFamily(token.getFamilyId(), now);
            throw new UnauthorizedException(INVALID);
        }
        token.markRotated(now);
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId()));
    }

    @Transactional
    public void revoke(String presented) {
        repository.findByTokenHash(hash(presented))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private IssuedRefreshToken issue(Long userId, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(validity);
        repository.save(new RefreshToken(userId, familyId, hash(value), now, expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

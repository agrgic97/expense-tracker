package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expensetracker.auth.TokenService.AccessToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void issuedTokenIsAcceptedByDecoder() {
        AuthProperties properties = props(SECRET, Duration.ofHours(1));
        SecretKey key = config.jwtSigningKey(properties);
        TokenService service = new TokenService(config.jwtEncoder(key), properties, Clock.systemUTC());

        AccessToken response = service.issue(user(42L, "toni"));
        Jwt jwt = config.jwtDecoder(key).decode(response.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(TokenService.USERNAME_CLAIM)).isEqualTo("toni");
        assertThat(jwt.getExpiresAt()).isEqualTo(response.expiresAt());
    }

    @Test
    void rejectsExpiredToken() {
        AuthProperties properties = props(SECRET, Duration.ofMinutes(5));
        SecretKey key = config.jwtSigningKey(properties);
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = new TokenService(config.jwtEncoder(key), properties, past).issue(user(1L, "a")).value();

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithOtherKey() {
        AuthProperties properties = props(SECRET, Duration.ofHours(1));
        AuthProperties other = props("another-secret-that-is-long-enough-xyz", Duration.ofHours(1));
        String token = new TokenService(config.jwtEncoder(config.jwtSigningKey(other)), other, Clock.systemUTC())
                .issue(user(1L, "a")).value();
        JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(properties));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> config.jwtSigningKey(props("short", Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    static AuthProperties props(String secret, Duration accessValidity) {
        return new AuthProperties(secret, accessValidity, Duration.ofDays(30), false, true,
                new AuthProperties.RateLimit(Duration.ofMinutes(15), 5, 20, 30));
    }

    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser(username, "{noop}x");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

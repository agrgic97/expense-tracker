package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expensetracker.auth.AuthDtos.TokenResponse;
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
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofHours(1), true);
        SecretKey key = config.jwtSigningKey(properties);
        TokenService service = new TokenService(config.jwtEncoder(key), properties, Clock.systemUTC());

        TokenResponse response = service.issue(user(42L, "toni"));
        Jwt jwt = config.jwtDecoder(key).decode(response.token());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(TokenService.USERNAME_CLAIM)).isEqualTo("toni");
        assertThat(jwt.getExpiresAt()).isEqualTo(response.expiresAt());
    }

    @Test
    void rejectsExpiredToken() {
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofMinutes(5), true);
        SecretKey key = config.jwtSigningKey(properties);
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = new TokenService(config.jwtEncoder(key), properties, past).issue(user(1L, "a")).token();

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithOtherKey() {
        AuthProperties properties = new AuthProperties(SECRET, Duration.ofHours(1), true);
        AuthProperties other = new AuthProperties("another-secret-that-is-long-enough-xyz", Duration.ofHours(1), true);
        String token = new TokenService(config.jwtEncoder(config.jwtSigningKey(other)), other, Clock.systemUTC())
                .issue(user(1L, "a")).token();
        JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(properties));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> config.jwtSigningKey(new AuthProperties("short", Duration.ofHours(1), true)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser(username, "{noop}x");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

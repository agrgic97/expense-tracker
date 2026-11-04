package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.LoginRateLimiterTest.MutableClock;
import com.expensetracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.expensetracker.auth.RefreshTokenService.Rotation;
import com.expensetracker.common.UnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final Map<String, RefreshToken> stored = new HashMap<>();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            stored.put((String) org.springframework.test.util.ReflectionTestUtils.getField(token, "tokenHash"), token);
            return token;
        });
        when(repository.findByTokenHash(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        service = new RefreshTokenService(repository, TokenServiceTest.props(null, Duration.ofMinutes(15)), clock);
    }

    @Test
    void storesOnlyHashOfToken() {
        IssuedRefreshToken issued = service.create(1L);

        assertThat(stored).containsOnlyKeys(RefreshTokenService.hash(issued.value()));
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
    }

    @Test
    void rotationIssuesNewTokenInSameFamily() {
        IssuedRefreshToken first = service.create(1L);

        Rotation rotation = service.rotate(first.value());

        assertThat(rotation.userId()).isEqualTo(1L);
        assertThat(rotation.newToken().value()).isNotEqualTo(first.value());
        RefreshToken old = stored.get(RefreshTokenService.hash(first.value()));
        RefreshToken next = stored.get(RefreshTokenService.hash(rotation.newToken().value()));
        assertThat(old.getRotatedAt()).isEqualTo(clock.instant());
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
    }

    @Test
    void parallelRefreshWithinGraceKeepsSession() {
        IssuedRefreshToken first = service.create(1L);
        service.rotate(first.value());
        clock.advance(Duration.ofSeconds(5));

        Rotation second = service.rotate(first.value());

        assertThat(second.userId()).isEqualTo(1L);
        assertThat(second.newToken()).isNull();
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reuseAfterGraceRevokesWholeFamily() {
        IssuedRefreshToken first = service.create(1L);
        service.rotate(first.value());
        clock.advance(RefreshTokenService.ROTATION_GRACE.plusSeconds(1));

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeFamily(eq(stored.get(RefreshTokenService.hash(first.value())).getFamilyId()), any());
    }

    @Test
    void rejectsUnknownAndExpiredTokens() {
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(UnauthorizedException.class);

        IssuedRefreshToken issued = service.create(1L);
        clock.advance(Duration.ofDays(31));
        assertThatThrownBy(() -> service.rotate(issued.value())).isInstanceOf(UnauthorizedException.class);
    }
}

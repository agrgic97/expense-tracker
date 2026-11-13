package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.mail.MailService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetServiceTest {

    private static final String IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private AppUser user;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        user = new AppUser("toni", "old-hash", "toni@example.com");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(mail.enabled()).thenReturn(true);
        AuthProperties properties = new AuthProperties(null, Duration.ofMinutes(15), Duration.ofDays(30), false, true,
                new AuthProperties.RateLimit(Duration.ofMinutes(15), 5, 20, 30), Duration.ofHours(1));
        service = new PasswordResetService(users, tokens, refreshTokens, passwordEncoder,
                new LoginRateLimiter(properties, Clock.fixed(NOW, ZoneOffset.UTC)), mail, properties,
                "https://example.com/", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void mailsSingleUseLinkAndStoresOnlyItsHash() {
        when(users.findByEmailIgnoreCase("toni@example.com")).thenReturn(Optional.of(user));

        service.requestReset(" toni@example.com ", IP);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("toni@example.com"), anyString(), text.capture());
        Matcher link = Pattern.compile("https://example.com/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(text.getValue());
        assertThat(link.find()).isTrue();
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "tokenHash")).isEqualTo(RefreshTokenService.hash(link.group(1)));
        verify(tokens).deleteAllOfUser(1L);
    }

    @Test
    void unknownAddressSendsNothing() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com", IP);

        verify(mail, never()).send(any(), any(), any());
        verify(tokens, never()).save(any());
    }

    @Test
    void limitsMailsPerAddress() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            service.requestReset("toni@example.com", IP);
        }

        assertThatThrownBy(() -> service.requestReset("toni@example.com", IP))
                .isInstanceOf(com.expensetracker.common.TooManyRequestsException.class);
    }

    @Test
    void resetSetsPasswordAndEndsAllLogins() {
        PasswordResetToken token = new PasswordResetToken(1L, RefreshTokenService.hash("abc"), NOW, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(RefreshTokenService.hash("abc"))).thenReturn(Optional.of(token));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("neues-passwort")).thenReturn("new-hash");

        service.resetPassword("abc", "neues-passwort", IP);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.usableAt(NOW)).isFalse();
        verify(refreshTokens).revokeAll(1L);
    }

    @Test
    void rejectsExpiredOrUsedToken() {
        PasswordResetToken expired = new PasswordResetToken(1L, RefreshTokenService.hash("old"), NOW.minusSeconds(7200), NOW.minusSeconds(1));
        when(tokens.findByTokenHash(RefreshTokenService.hash("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resetPassword("old", "neues-passwort", IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordResetService.INVALID);
        verify(refreshTokens, never()).revokeAll(any());
    }
}

package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.AuthDtos.LoginRequest;
import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.ConflictException;
import com.expensetracker.common.TooManyRequestsException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private static final String IP = "203.0.113.7";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);

    private AuthService service(boolean registrationEnabled) {
        return new AuthService(users, passwordEncoder, authenticationManager, tokens,
                mock(RefreshTokenService.class), rateLimiter, categories,
                new AuthProperties(null, Duration.ofMinutes(15), Duration.ofDays(30), false, registrationEnabled,
                        new AuthProperties.RateLimit(Duration.ofMinutes(15), 5, 20, 30), Duration.ofHours(1)));
    }

    @Test
    void firstUserAdoptsExistingData() {
        when(passwordEncoder.encode("geheim123")).thenReturn("hash");
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(users.count()).thenReturn(1L);

        service(true).register(new RegisterRequest("toni", "geheim123", null), IP);

        verify(categories).initializeFor(1L, true);
    }

    @Test
    void laterUsersStartWithDefaultsOnly() {
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 2L));
        when(users.count()).thenReturn(2L);

        service(true).register(new RegisterRequest("anna", "geheim123", null), IP);

        verify(categories).initializeFor(2L, false);
    }

    @Test
    void rejectsTakenUsername() {
        when(users.existsByUsernameIgnoreCase("Toni")).thenReturn(true);

        assertThatThrownBy(() -> service(true).register(new RegisterRequest("Toni", "geheim123", null), IP))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsRegistrationWhenDisabled() {
        assertThatThrownBy(() -> service(false).register(new RegisterRequest("toni", "geheim123", null), IP))
                .isInstanceOf(ResponseStatusException.class);
        verify(users, never()).save(any());
    }

    @Test
    void failedLoginIsCountedAndSuccessfulLoginResetsCounter() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service(true).login(new LoginRequest(" toni ", "falsch"), IP))
                .isInstanceOf(BadCredentialsException.class);

        verify(rateLimiter).checkAllowed(IP, "toni");
        verify(rateLimiter).recordFailure(IP, "toni");
        verify(rateLimiter, never()).recordSuccess(any(), any());
    }

    @Test
    void blockedLoginDoesNotCheckPassword() {
        doThrow(new TooManyRequestsException("zu viele", Duration.ofMinutes(5)))
                .when(rateLimiter).checkAllowed(IP, "toni");

        assertThatThrownBy(() -> service(true).login(new LoginRequest("toni", "geheim123"), IP))
                .isInstanceOf(TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    private static AppUser withId(AppUser user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

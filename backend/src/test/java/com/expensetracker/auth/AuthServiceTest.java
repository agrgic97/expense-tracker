package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.ConflictException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final CategoryService categories = mock(CategoryService.class);

    private AuthService service(boolean registrationEnabled) {
        return new AuthService(users, passwordEncoder, mock(AuthenticationManager.class), tokens, categories,
                new AuthProperties(null, Duration.ofDays(1), registrationEnabled));
    }

    @Test
    void firstUserAdoptsExistingData() {
        when(passwordEncoder.encode("geheim123")).thenReturn("hash");
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(users.count()).thenReturn(1L);

        service(true).register(new RegisterRequest("toni", "geheim123"));

        verify(categories).initializeFor(1L, true);
    }

    @Test
    void laterUsersStartWithDefaultsOnly() {
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 2L));
        when(users.count()).thenReturn(2L);

        service(true).register(new RegisterRequest("anna", "geheim123"));

        verify(categories).initializeFor(2L, false);
    }

    @Test
    void rejectsTakenUsername() {
        when(users.existsByUsernameIgnoreCase("Toni")).thenReturn(true);

        assertThatThrownBy(() -> service(true).register(new RegisterRequest("Toni", "geheim123")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsRegistrationWhenDisabled() {
        assertThatThrownBy(() -> service(false).register(new RegisterRequest("toni", "geheim123")))
                .isInstanceOf(ResponseStatusException.class);
        verify(users, never()).save(any());
    }

    private static AppUser withId(AppUser user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

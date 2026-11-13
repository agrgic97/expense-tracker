package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.AccountResponse;
import com.expensetracker.auth.AuthDtos.ChangePasswordRequest;
import com.expensetracker.auth.AuthDtos.LoginRequest;
import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.expensetracker.auth.RefreshTokenService.Rotation;
import com.expensetracker.auth.TokenService.AccessToken;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.ConflictException;
import com.expensetracker.common.NotFoundException;
import com.expensetracker.common.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    /** {@code refreshToken} is null when the client keeps its current refresh token. */
    public record Session(AppUser user, AccessToken accessToken, IssuedRefreshToken refreshToken) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final LoginRateLimiter rateLimiter;
    private final CategoryService categories;
    private final AuthProperties properties;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, TokenService tokens,
                       RefreshTokenService refreshTokens, LoginRateLimiter rateLimiter,
                       CategoryService categories, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.rateLimiter = rateLimiter;
        this.categories = categories;
        this.properties = properties;
    }

    public boolean registrationEnabled() {
        return properties.registrationEnabled();
    }

    @Transactional
    public Session register(RegisterRequest request, String clientIp) {
        rateLimiter.checkAllowed(clientIp, null);
        rateLimiter.recordAttempt(clientIp);
        if (!properties.registrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registrierung ist deaktiviert");
        }
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Benutzername ist bereits vergeben");
        }
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, null);
        AppUser user = users.save(new AppUser(username, passwordEncoder.encode(request.password()), email));
        boolean firstUser = users.count() == 1;
        categories.initializeFor(user.getId(), firstUser);
        return startSession(user);
    }

    public Session login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        rateLimiter.checkAllowed(clientIp, username);
        rateLimiter.recordAttempt(clientIp);
        try {
            // Throws BadCredentialsException for unknown users and wrong passwords alike
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(clientIp, username);
            throw e;
        }
        rateLimiter.recordSuccess(clientIp, username);
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return startSession(user);
    }

    public Session refresh(String refreshToken) {
        Rotation rotation = refreshTokens.rotate(refreshToken);
        AppUser user = users.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("Sitzung abgelaufen. Bitte erneut anmelden."));
        return new Session(user, tokens.issue(user), rotation.newToken());
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    public AccountResponse account(Long userId) {
        AppUser user = find(userId);
        return new AccountResponse(user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    @Transactional
    public AccountResponse changeEmail(Long userId, String email) {
        AppUser user = find(userId);
        String normalized = normalizeEmail(email);
        ensureEmailAvailable(normalized, userId);
        user.setEmail(normalized);
        return new AccountResponse(user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    /**
     * Changes the password and ends all other logins. Wrong current passwords count as failed logins, so this
     * cannot be used to guess the password of a session left open.
     */
    @Transactional
    public Session changePassword(Long userId, ChangePasswordRequest request, String clientIp) {
        AppUser user = find(userId);
        rateLimiter.checkAllowed(clientIp, user.getUsername());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            rateLimiter.recordFailure(clientIp, user.getUsername());
            throw new IllegalArgumentException("Das aktuelle Passwort ist falsch");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(userId);
        return startSession(user);
    }

    static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    private void ensureEmailAvailable(String email, Long ownId) {
        if (email == null) {
            return;
        }
        users.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Diese E-Mail-Adresse wird bereits verwendet");
                });
    }

    private AppUser find(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Benutzer nicht gefunden"));
    }

    private Session startSession(AppUser user) {
        return new Session(user, tokens.issue(user), refreshTokens.create(user.getId()));
    }
}

package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.LoginRequest;
import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.auth.AuthDtos.TokenResponse;
import com.expensetracker.category.CategoryService;
import com.expensetracker.common.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final CategoryService categories;
    private final AuthProperties properties;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, TokenService tokens,
                       CategoryService categories, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.categories = categories;
        this.properties = properties;
    }

    public boolean registrationEnabled() {
        return properties.registrationEnabled();
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (!properties.registrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registrierung ist deaktiviert");
        }
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Benutzername ist bereits vergeben");
        }
        AppUser user = users.save(new AppUser(username, passwordEncoder.encode(request.password())));
        boolean firstUser = users.count() == 1;
        categories.initializeFor(user.getId(), firstUser);
        return tokens.issue(user);
    }

    public TokenResponse login(LoginRequest request) {
        // Throws BadCredentialsException for unknown users and wrong passwords alike
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim(), request.password()));
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return tokens.issue(user);
    }
}

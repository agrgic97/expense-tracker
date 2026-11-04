package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.AuthConfig;
import com.expensetracker.auth.AuthDtos.LoginRequest;
import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.auth.AuthDtos.TokenResponse;
import com.expensetracker.auth.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final CurrentUser currentUser;

    public AuthController(AuthService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/config")
    public AuthConfig config() {
        return new AuthConfig(service.registrationEnabled());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return new UserResponse(currentUser.id(), currentUser.username());
    }
}

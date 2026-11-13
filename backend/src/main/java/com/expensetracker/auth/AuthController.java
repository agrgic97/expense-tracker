package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.AuthConfig;
import com.expensetracker.auth.AuthDtos.LoginRequest;
import com.expensetracker.auth.AuthDtos.PasswordResetConfirmation;
import com.expensetracker.auth.AuthDtos.PasswordResetRequest;
import com.expensetracker.auth.AuthDtos.RegisterRequest;
import com.expensetracker.auth.AuthDtos.SessionResponse;
import com.expensetracker.auth.AuthDtos.UserResponse;
import com.expensetracker.auth.AuthService.Session;
import com.expensetracker.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final PasswordResetService passwordReset;
    private final AuthCookies cookies;
    private final CurrentUser currentUser;

    public AuthController(AuthService service, PasswordResetService passwordReset, AuthCookies cookies,
                          CurrentUser currentUser) {
        this.service = service;
        this.passwordReset = passwordReset;
        this.cookies = cookies;
        this.currentUser = currentUser;
    }

    @GetMapping("/config")
    public AuthConfig config() {
        return new AuthConfig(service.registrationEnabled(), passwordReset.enabled());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
                                    HttpServletResponse response) {
        return respond(service.register(body, request.getRemoteAddr()), response);
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                 HttpServletResponse response) {
        return respond(service.login(body, request.getRemoteAddr()), response);
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookies.refreshToken(request)
                .orElseThrow(() -> new UnauthorizedException("Nicht angemeldet"));
        try {
            return respond(service.refresh(token), response);
        } catch (UnauthorizedException e) {
            cookies.clear(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.refreshToken(request).ifPresent(service::logout);
        cookies.clear(response);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        passwordReset.requestReset(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation body, HttpServletRequest request,
                                     HttpServletResponse response) {
        passwordReset.resetPassword(body.token(), body.password(), request.getRemoteAddr());
        cookies.clear(response);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return new UserResponse(currentUser.id(), currentUser.username());
    }

    private SessionResponse respond(Session session, HttpServletResponse response) {
        cookies.write(response, session.accessToken(), session.refreshToken());
        return new SessionResponse(session.user().getUsername(), session.accessToken().expiresAt());
    }
}

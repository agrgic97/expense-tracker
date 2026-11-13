package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.AccountResponse;
import com.expensetracker.auth.AuthDtos.ChangeEmailRequest;
import com.expensetracker.auth.AuthDtos.ChangePasswordRequest;
import com.expensetracker.auth.AuthDtos.SessionResponse;
import com.expensetracker.auth.AuthService.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings of the logged-in account. Not under /api/auth, where the access token cookie is ignored. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AuthService service;
    private final AuthCookies cookies;
    private final CurrentUser currentUser;

    public AccountController(AuthService service, AuthCookies cookies, CurrentUser currentUser) {
        this.service = service;
        this.cookies = cookies;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AccountResponse account() {
        return service.account(currentUser.id());
    }

    @PutMapping("/email")
    public AccountResponse changeEmail(@Valid @RequestBody ChangeEmailRequest body) {
        return service.changeEmail(currentUser.id(), body.email());
    }

    /** Other devices are logged out; this one gets fresh tokens. */
    @PutMapping("/password")
    public SessionResponse changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request,
                                          HttpServletResponse response) {
        Session session = service.changePassword(currentUser.id(), body, request.getRemoteAddr());
        cookies.write(response, session.accessToken(), session.refreshToken());
        return new SessionResponse(session.user().getUsername(), session.accessToken().expiresAt());
    }
}

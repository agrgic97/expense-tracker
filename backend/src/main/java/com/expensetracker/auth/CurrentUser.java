package com.expensetracker.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the user of the current request from the validated JWT. */
@Component
public class CurrentUser {

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Long.valueOf(token.getToken().getSubject());
        }
        throw new IllegalStateException("Kein angemeldeter Benutzer");
    }

    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString(TokenService.USERNAME_CLAIM);
        }
        throw new IllegalStateException("Kein angemeldeter Benutzer");
    }
}

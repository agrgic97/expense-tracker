package com.expensetracker.auth;

import com.expensetracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.expensetracker.auth.TokenService.AccessToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Tokens live in httpOnly cookies so page scripts (and thus XSS) cannot read them. The refresh token is
 * only sent to the auth endpoints.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    static final String ACCESS_PATH = "/api";
    static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;
    private final Clock clock;

    public AuthCookies(AuthProperties properties, Clock clock) {
        this.secure = properties.cookieSecure();
        this.clock = clock;
    }

    public void write(HttpServletResponse response, AccessToken access, IssuedRefreshToken refresh) {
        add(response, ACCESS, access.value(), ACCESS_PATH, untilExpiry(access.expiresAt()));
        if (refresh != null) {
            add(response, REFRESH, refresh.value(), REFRESH_PATH, untilExpiry(refresh.expiresAt()));
        }
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", ACCESS_PATH, Duration.ZERO);
        add(response, REFRESH, "", REFRESH_PATH, Duration.ZERO);
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return value(request, REFRESH);
    }

    public static Optional<String> value(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(v -> !v.isBlank());
    }

    private Duration untilExpiry(Instant expiresAt) {
        return Duration.between(clock.instant(), expiresAt);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

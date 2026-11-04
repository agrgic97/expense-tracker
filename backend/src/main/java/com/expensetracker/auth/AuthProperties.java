package com.expensetracker.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenValidity,
        @DefaultValue("30d") Duration refreshTokenValidity,
        @DefaultValue("false") boolean cookieSecure,
        @DefaultValue("true") boolean registrationEnabled,
        @DefaultValue RateLimit rateLimit) {

    public record RateLimit(
            @DefaultValue("15m") Duration window,
            @DefaultValue("5") int maxFailuresPerUserAndIp,
            @DefaultValue("20") int maxFailuresPerUser,
            @DefaultValue("30") int maxAttemptsPerIp) {
    }
}

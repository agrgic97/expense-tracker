package com.expensetracker.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("7d") Duration tokenValidity,
        @DefaultValue("true") boolean registrationEnabled) {
}

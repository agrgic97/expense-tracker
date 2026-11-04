package com.expensetracker.auth;

import com.expensetracker.auth.AuthDtos.TokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public static final String ISSUER = "expense-tracker";
    public static final String USERNAME_CLAIM = "username";

    private final JwtEncoder encoder;
    private final Duration validity;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.validity = properties.tokenValidity();
        this.clock = clock;
    }

    public TokenResponse issue(AppUser user) {
        // JWT timestamps have second precision; truncate so the reported expiry matches the token
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(validity);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(USERNAME_CLAIM, user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, expiresAt, user.getUsername());
    }
}

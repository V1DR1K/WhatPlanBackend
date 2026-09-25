package com.wherefood.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CentralJwt {
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final int MAX_PUBLIC_KEYS = 3;
    private final List<RSAPublicKey> publicKeys;
    private final String issuer;
    private final String audience;
    private final Duration maxAccessTokenTtl;

    public CentralJwt(@Value("${app.auth-public-key-pem}") String pem,
                      @Value("${app.auth-issuer}") String issuer,
                      @Value("${app.auth-audience}") String audience,
                      @Value("${app.auth-previous-public-key-pems:}") String previousPems,
                      @Value("${app.auth-max-access-token-ttl-seconds:900}") long maxAccessTokenTtlSeconds) {
        this(parseKeys(pem, previousPems), issuer, audience, maxAccessTokenTtlSeconds);
    }

    public UUID subject(String token) {
        JwtException lastFailure = null;
        io.jsonwebtoken.Jws<Claims> parsed = null;
        for (RSAPublicKey key : publicKeys) {
            try {
                parsed = Jwts.parser().verifyWith(key)
                        .requireIssuer(issuer)
                        .requireAudience(audience)
                        .require("token_type", ACCESS_TOKEN_TYPE)
                        .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                        .build().parseSignedClaims(token);
                break;
            } catch (JwtException exception) {
                lastFailure = exception;
            }
        }
        if (parsed == null) throw lastFailure == null ? new IllegalArgumentException("Invalid central JWT") : lastFailure;
        if (!"RS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Central JWT must use RS256");
        }

        Claims claims = parsed.getPayload();
        Date issuedAt = claims.getIssuedAt();
        Date notBefore = claims.getNotBefore();
        Date expiresAt = claims.getExpiration();
        if (claims.getSubject() == null || issuedAt == null || notBefore == null || expiresAt == null) {
            throw new IllegalArgumentException("Central access JWT is missing a required time or subject claim");
        }
        Instant now = Instant.now();
        if (issuedAt.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                || !expiresAt.toInstant().isAfter(issuedAt.toInstant())
                || Duration.between(issuedAt.toInstant(), expiresAt.toInstant()).compareTo(maxAccessTokenTtl) > 0) {
            throw new IllegalArgumentException("Central access JWT has an invalid or excessive lifetime");
        }
        try {
            UUID subject = UUID.fromString(claims.getSubject());
            if (!subject.toString().equalsIgnoreCase(claims.getSubject())) {
                throw new IllegalArgumentException("Central JWT subject must use canonical UUID format");
            }
            return subject;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Central JWT subject must be a UUID", exception);
        }
    }

    private static List<RSAPublicKey> parseKeys(String activePem, String previousPems) {
        if (activePem == null || activePem.isBlank()) {
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is required");
        }
        List<RSAPublicKey> keys = new ArrayList<>();
        keys.add(parsePublicKey(activePem));
        if (previousPems != null && !previousPems.isBlank()) {
            for (String pem : previousPems.split(",")) {
                if (!pem.isBlank()) keys.add(parsePublicKey(pem));
            }
        }
        if (keys.size() > MAX_PUBLIC_KEYS) {
            throw new IllegalStateException("At most two previous AUTH_PUBLIC_KEY_PEM values are allowed");
        }
        return List.copyOf(keys);
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is required");
        }
        try {
            String encoded = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM must use an RSA key of at least 2048 bits");
            }
            return key;
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM must contain an RSA public key", ex);
        }
    }

    private CentralJwt(List<RSAPublicKey> publicKeys, String issuer, String audience, long maxAccessTokenTtlSeconds) {
        if (issuer == null || issuer.isBlank()) throw new IllegalStateException("AUTH_JWT_ISSUER is required");
        if (audience == null || audience.isBlank()) throw new IllegalStateException("AUTH_JWT_AUDIENCE is required");
        if (maxAccessTokenTtlSeconds < 1 || maxAccessTokenTtlSeconds > 3600) {
            throw new IllegalStateException("AUTH_MAX_ACCESS_TOKEN_TTL_SECONDS must be between 1 and 3600");
        }
        this.publicKeys = publicKeys;
        this.issuer = issuer.trim();
        this.audience = audience.trim();
        this.maxAccessTokenTtl = Duration.ofSeconds(maxAccessTokenTtlSeconds);
    }
}

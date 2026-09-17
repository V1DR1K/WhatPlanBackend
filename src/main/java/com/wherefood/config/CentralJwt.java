package com.wherefood.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CentralJwt {
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final String accessTokenClaim;
    private final String accessTokenValue;

    public CentralJwt(@Value("${app.auth-public-key-pem}") String pem,
                      @Value("${app.auth-issuer}") String issuer,
                      @Value("${app.auth-audience:}") String audience,
                      @Value("${app.auth-access-token-claim:}") String accessTokenClaim,
                      @Value("${app.auth-access-token-value:access}") String accessTokenValue) {
        this.publicKey = parsePublicKey(pem);
        this.issuer = issuer;
        this.audience = audience == null ? "" : audience.trim();
        this.accessTokenClaim = accessTokenClaim == null ? "" : accessTokenClaim.trim();
        this.accessTokenValue = accessTokenValue;
    }

    public CentralJwt(String pem, String issuer) {
        this(pem, issuer, "", "", "access");
    }

    public UUID subject(String token) {
        var parser = Jwts.parser().verifyWith(publicKey).requireIssuer(issuer);
        if (!audience.isBlank()) parser.requireAudience(audience);
        if (!accessTokenClaim.isBlank()) parser.require(accessTokenClaim, accessTokenValue);
        Claims claims = parser.build().parseSignedClaims(token).getPayload();
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
            throw new IllegalArgumentException("Central JWT must contain a valid subject and expiration");
        }
        return UUID.fromString(claims.getSubject());
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
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception ex) {
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM must contain an RSA public key", ex);
        }
    }
}

package com.wherefood.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CentralJwt {
    private final RSAPublicKey publicKey;
    private final String issuer;

    public CentralJwt(@Value("${app.auth-public-key-pem}") String pem,
                      @Value("${app.auth-issuer:central-auth-service}") String issuer) {
        this.publicKey = parsePublicKey(pem);
        this.issuer = issuer;
    }

    public UUID subject(String token) {
        Claims claims = Jwts.parser().verifyWith(publicKey).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
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

package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CentralJwtTest {
    @Test
    void acceptsOnlyAValidCentralIssuer() throws Exception {
        KeyPair keys = rsaKeys();
        String token = token(keys, "central-auth-service");
        CentralJwt jwt = new CentralJwt(publicPem((RSAPublicKey) keys.getPublic()), "central-auth-service");

        assertEquals(UUID.fromString("0d7246aa-f222-4719-b554-e7e06357a256"), jwt.subject(token));
    }

    @Test
    void rejectsTokensIssuedByAnotherService() throws Exception {
        KeyPair keys = rsaKeys();
        String token = token(keys, "another-service");
        CentralJwt jwt = new CentralJwt(publicPem((RSAPublicKey) keys.getPublic()), "central-auth-service");

        assertThrows(RuntimeException.class, () -> jwt.subject(token));
    }

    private static KeyPair rsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String token(KeyPair keys, String issuer) {
        return Jwts.builder()
                .subject("0d7246aa-f222-4719-b554-e7e06357a256")
                .issuer(issuer)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String publicPem(RSAPublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}

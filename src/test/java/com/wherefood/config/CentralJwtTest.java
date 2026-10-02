package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CentralJwtTest {
    private static final String ISSUER = "central-auth-service";
    private static final String AUDIENCE = "whatplan";
    private static final UUID USER_ID = UUID.fromString("0d7246aa-f222-4719-b554-e7e06357a256");

    @Test
    void acceptsOnlyAnAccessTokenWithTheRequiredClaims() throws Exception {
        KeyPair keys = rsaKeys();
        CentralJwt jwt = jwt(keys);

        assertEquals(USER_ID, jwt.subject(token(keys, Claims.valid())));
    }

    @Test
    void rejectsIssuerAudienceAndTokenTypeMismatches() throws Exception {
        KeyPair keys = rsaKeys();
        CentralJwt jwt = jwt(keys);

        assertInvalid(jwt, token(keys, Claims.valid().withIssuer("another-service")));
        assertInvalid(jwt, token(keys, Claims.valid().withAudience("another-api")));
        assertInvalid(jwt, token(keys, Claims.valid().withType("refresh")));
        assertInvalid(jwt, token(keys, Claims.valid().withoutAudience()));
        assertInvalid(jwt, token(keys, Claims.valid().withoutType()));
    }

    @Test
    void rejectsMissingOrInvalidTimeClaimsAndExcessiveLifetime() throws Exception {
        KeyPair keys = rsaKeys();
        CentralJwt jwt = jwt(keys);

        assertInvalid(jwt, token(keys, Claims.valid().withoutIssuedAt()));
        assertInvalid(jwt, token(keys, Claims.valid().withoutNotBefore()));
        assertInvalid(jwt, token(keys, Claims.valid().withoutExpiration()));
        assertInvalid(jwt, token(keys, Claims.valid().withNotBefore(Instant.now().plusSeconds(120))));
        assertInvalid(jwt, token(keys, Claims.valid().withExpiration(Instant.now().minusSeconds(120))));
        assertInvalid(jwt, token(keys, Claims.valid().withLifetime(901)));
    }

    @Test
    void rejectsInvalidSubjectsUnsupportedAlgorithmsAndBadSignatures() throws Exception {
        KeyPair trusted = rsaKeys();
        KeyPair attacker = rsaKeys();
        CentralJwt jwt = jwt(trusted);

        assertInvalid(jwt, token(trusted, Claims.valid().withSubject("not-a-uuid")));
        assertInvalid(jwt, token(trusted, Claims.valid(), "RS512"));
        assertInvalid(jwt, token(attacker, Claims.valid()));
    }

    @Test
    void acceptsCurrentAndPreviousKeysDuringBoundedRotationWindow() throws Exception {
        KeyPair oldKey = rsaKeys();
        KeyPair newKey = rsaKeys();
        CentralJwt jwt = new CentralJwt(publicPem((RSAPublicKey) newKey.getPublic()), ISSUER, AUDIENCE,
                publicPem((RSAPublicKey) oldKey.getPublic()), 900);

        assertEquals(USER_ID, jwt.subject(token(oldKey, Claims.valid())));
        assertEquals(USER_ID, jwt.subject(token(newKey, Claims.valid())));
        assertThrows(IllegalStateException.class, () -> new CentralJwt(
                publicPem((RSAPublicKey) newKey.getPublic()), ISSUER, AUDIENCE,
                String.join(",", publicPem((RSAPublicKey) oldKey.getPublic()),
                        publicPem((RSAPublicKey) rsaKeys().getPublic()),
                        publicPem((RSAPublicKey) rsaKeys().getPublic())), 900));
    }

    @Test
    void requiresExplicitIssuerAudienceAndSaneTtlConfiguration() throws Exception {
        KeyPair keys = rsaKeys();
        String pem = publicPem((RSAPublicKey) keys.getPublic());
        assertThrows(IllegalStateException.class, () -> new CentralJwt(pem, " ", AUDIENCE, "", 900));
        assertThrows(IllegalStateException.class, () -> new CentralJwt(pem, ISSUER, " ", "", 900));
        assertThrows(IllegalStateException.class, () -> new CentralJwt(pem, ISSUER, AUDIENCE, "", 3601));
    }

    private static CentralJwt jwt(KeyPair keys) {
        return new CentralJwt(publicPem((RSAPublicKey) keys.getPublic()), ISSUER, AUDIENCE, "", 900);
    }

    private static void assertInvalid(CentralJwt jwt, String token) {
        assertThrows(RuntimeException.class, () -> jwt.subject(token));
    }

    private static String token(KeyPair keys, Claims claims) {
        return token(keys, claims, "RS256");
    }

    private static String token(KeyPair keys, Claims claims, String algorithm) {
        var builder = Jwts.builder().subject(claims.subject()).issuer(claims.issuer());
        if (claims.audience() != null) builder.audience().add(claims.audience()).and();
        if (claims.type() != null) builder.claim("token_type", claims.type());
        if (claims.issuedAt() != null) builder.issuedAt(Date.from(claims.issuedAt()));
        if (claims.notBefore() != null) builder.notBefore(Date.from(claims.notBefore()));
        if (claims.expiration() != null) builder.expiration(Date.from(claims.expiration()));
        if ("RS512".equals(algorithm)) return builder.signWith(keys.getPrivate(), Jwts.SIG.RS512).compact();
        return builder.signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static KeyPair rsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicPem(RSAPublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private record Claims(String subject, String issuer, String audience, String type,
                          Instant issuedAt, Instant notBefore, Instant expiration) {
        static Claims valid() {
            Instant now = Instant.now();
            return new Claims(USER_ID.toString(), ISSUER, AUDIENCE, "access",
                    now.minusSeconds(5), now.minusSeconds(5), now.plusSeconds(300));
        }
        Claims withIssuer(String value) { return new Claims(subject, value, audience, type, issuedAt, notBefore, expiration); }
        Claims withAudience(String value) { return new Claims(subject, issuer, value, type, issuedAt, notBefore, expiration); }
        Claims withoutAudience() { return new Claims(subject, issuer, null, type, issuedAt, notBefore, expiration); }
        Claims withType(String value) { return new Claims(subject, issuer, audience, value, issuedAt, notBefore, expiration); }
        Claims withoutType() { return new Claims(subject, issuer, audience, null, issuedAt, notBefore, expiration); }
        Claims withSubject(String value) { return new Claims(value, issuer, audience, type, issuedAt, notBefore, expiration); }
        Claims withoutIssuedAt() { return new Claims(subject, issuer, audience, type, null, notBefore, expiration); }
        Claims withoutNotBefore() { return new Claims(subject, issuer, audience, type, issuedAt, null, expiration); }
        Claims withoutExpiration() { return new Claims(subject, issuer, audience, type, issuedAt, notBefore, null); }
        Claims withNotBefore(Instant value) { return new Claims(subject, issuer, audience, type, issuedAt, value, expiration); }
        Claims withExpiration(Instant value) { return new Claims(subject, issuer, audience, type, issuedAt, notBefore, value); }
        Claims withLifetime(long seconds) { return new Claims(subject, issuer, audience, type, issuedAt, notBefore, issuedAt.plusSeconds(seconds)); }
    }
}

package si.um.feri.dotaops.backend.auth.service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class SupabaseJwtTestSupport {

    public static final String SECRET = "01234567890123456789012345678901";
    public static final String ISSUER = "https://test-project.supabase.co/auth/v1";
    public static final String AUDIENCE = "authenticated";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SupabaseJwtTestSupport() {
    }

    public static String token(UUID subject, Instant now) throws Exception {
        return token(subject, now, ISSUER, AUDIENCE, SECRET);
    }

    public static String tokenWithMetadata(
            UUID subject,
            Instant now,
            String metadataClaim,
            String desiredRole
    ) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(subject.toString())
                .claim("email", "player@example.com")
                .claim("role", "authenticated")
                .claim(metadataClaim, Map.of("desired_role", desiredRole))
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }

    public static String token(
            UUID subject,
            Instant now,
            String issuer,
            String audience,
            String secret
    ) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject.toString())
                .claim("email", "player@example.com")
                .claim("role", "authenticated")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    public static String expiredToken(UUID subject, Instant now) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(subject.toString())
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.minusSeconds(120)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }

    public static RSAKey rsaKey(String keyId) throws Exception {
        return new RSAKeyGenerator(2048)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    public static String jwksJson(RSAKey rsaKey) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(new JWKSet(rsaKey.toPublicJWK()).toJSONObject());
    }

    public static String rsaToken(
            UUID subject,
            Instant now,
            String issuer,
            String audience,
            RSAKey rsaKey
    ) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject.toString())
                .claim("email", "player@example.com")
                .claim("role", "authenticated")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaKey.getKeyID())
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}

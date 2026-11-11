package org.example.back_end_iot.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final long ttl;

    public JwtService(@Value("${iot.jwt.secret:}") String secret,
                      @Value("${iot.jwt.issuer:iot-command-center}") String issuer,
                      @Value("${iot.jwt.ttl-seconds:3600}") long ttl) {
        if (issuer.isBlank() || ttl < 60 || ttl > 86400)
            throw new IllegalArgumentException("JWT issuer must be set; TTL must be 60..86400 seconds");
        byte[] bytes;
        if (secret.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(JwtService.class).warn(
                    "IOT_JWT_SECRET is unset: using a temporary random key. Tokens expire on backend restart.");
        } else {
            try { bytes = Base64.getDecoder().decode(secret); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("IOT_JWT_SECRET must be Base64", ex); }
            if (bytes.length < 32) throw new IllegalArgumentException("IOT_JWT_SECRET must contain at least 32 random bytes in Base64");
        }
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("iot-web")),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank()),
                new JwtClaimValidator<Instant>("iat", iat -> iat != null && !iat.isAfter(Instant.now())),
                new JwtClaimValidator<Instant>("exp", exp -> exp != null)));
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public Jwt issue(String username) { return issueAt(username, Instant.now()); }

    Jwt issueAt(String username, Instant now) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(username).audience(List.of("iot-web"))
                .issuedAt(now).expiresAt(now.plusSeconds(ttl)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }

    public JwtDecoder decoder() { return decoder; }
    public long ttlSeconds() { return ttl; }
}

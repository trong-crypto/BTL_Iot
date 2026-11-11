package org.example.back_end_iot.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    private final String secret = Base64.getEncoder().encodeToString(new byte[32]);
    private JwtService service() { return new JwtService(secret, "iot-command-center", 3600); }

    @Test void signsAndVerifiesSubjectIssuerAudienceAndExpiry() {
        var service = service();
        var jwt = service.decoder().decode(service.issue("admin").getTokenValue());
        assertEquals("admin", jwt.getSubject());
        assertEquals("iot-command-center", jwt.getClaimAsString("iss"));
        assertTrue(jwt.getAudience().contains("iot-web"));
        assertEquals(3600, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
    }

    @Test void rejectsExpiredFutureWrongIssuerAndWrongSignature() {
        var service = service();
        assertThrows(JwtException.class, () -> service.decoder().decode(
                service.issueAt("admin", Instant.now().minusSeconds(3601)).getTokenValue()));
        assertThrows(JwtException.class, () -> service.decoder().decode(
                service.issueAt("admin", Instant.now().plusSeconds(120)).getTokenValue()));
        assertThrows(JwtException.class, () -> service.decoder().decode(
                new JwtService(secret, "other-issuer", 3600).issue("admin").getTokenValue()));
        byte[] other = new byte[32]; other[0] = 1;
        assertThrows(JwtException.class, () -> service.decoder().decode(
                new JwtService(Base64.getEncoder().encodeToString(other), "iot-command-center", 3600)
                        .issue("admin").getTokenValue()));
        assertThrows(JwtException.class, () -> service.decoder().decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9."));
    }

    @Test void rejectsInvalidKeyOrTtlAndUsesDifferentTemporaryKeys() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("invalid!", "issuer", 3600));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("YQ==", "issuer", 3600));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(secret, "issuer", 0));
        var first = new JwtService("", "issuer", 3600);
        var second = new JwtService("", "issuer", 3600);
        assertThrows(JwtException.class, () -> second.decoder().decode(first.issue("admin").getTokenValue()));
    }
}

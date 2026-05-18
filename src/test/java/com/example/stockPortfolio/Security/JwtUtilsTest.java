package com.example.stockPortfolio.Security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boundary tests on the H5 startup secret-validation hook. */
class JwtUtilsTest {

    private JwtUtils withSecret(String secret, long exp) {
        JwtUtils u = new JwtUtils();
        ReflectionTestUtils.setField(u, "secret", secret);
        ReflectionTestUtils.setField(u, "expiration", exp);
        return u;
    }

    @Test
    @DisplayName("startup fails clearly when JWT_SECRET is missing")
    void missingSecret() {
        JwtUtils u = withSecret("", 86400000L);
        assertThatThrownBy(u::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("startup fails clearly when JWT_SECRET isn't Base64")
    void notBase64() {
        // The default placeholder used to ship in .env.example
        JwtUtils u = withSecret("your_super_secret_jwt_key_change_in_production!!!*", 86400000L);
        // (Some characters here aren't valid Base64 alphabet → decoder throws.)
        assertThatThrownBy(u::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("startup fails when decoded secret is too short for HS256")
    void tooShort() {
        // "abc" base64 → length 2 bytes → < 32 required
        JwtUtils u = withSecret("YWJj", 86400000L);
        assertThatThrownBy(u::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(">= 32");
    }

    @Test
    @DisplayName("startup fails when expiration is non-positive")
    void badExpiration() {
        // 64 random bytes base64-encoded → safe-length secret
        String validSecret =
                "Yzg2OWZkOWY3OWFhYzM4ZGY3MzdmZGM2YzMyOTQ4OTk2NWE5NjdmYjFmYjY3OWY3MmQ3ZmZjY2QxZTIyMDhlMA==";
        JwtUtils u = withSecret(validSecret, 0L);
        assertThatThrownBy(u::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("startup passes for a real openssl-style secret")
    void happyPath() {
        String validSecret =
                "Yzg2OWZkOWY3OWFhYzM4ZGY3MzdmZGM2YzMyOTQ4OTk2NWE5NjdmYjFmYjY3OWY3MmQ3ZmZjY2QxZTIyMDhlMA==";
        JwtUtils u = withSecret(validSecret, 86_400_000L);
        u.validateSecret(); // should not throw
        assertThat(true).isTrue();
    }
}

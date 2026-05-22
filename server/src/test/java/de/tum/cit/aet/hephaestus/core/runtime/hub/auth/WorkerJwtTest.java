package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorkerJwtTest extends BaseUnitTest {

    private WorkerKeyRing keyRing;
    private WorkerTokenProperties properties;
    private WorkerJwtIssuer issuer;
    private WorkerTokenDenylist denylist;
    private JavaJwtWorkerJwtVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new WorkerTokenProperties(
            "hephaestus-test",
            "hephaestus-worker",
            Duration.ofMinutes(15),
            "register-me",
            List.of(),
            null,
            null
        );
        keyRing = WorkerKeyRing.fromConfig(properties);
        issuer = new WorkerJwtIssuer(keyRing, properties);
        denylist = mock(WorkerTokenDenylist.class);
        lenient().when(denylist.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        verifier = new JavaJwtWorkerJwtVerifier(keyRing, properties, denylist, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void issuedTokenVerifiesWithExpectedClaims() throws Exception {
        WorkerJwtIssuer.IssuedWorkerJwt issued = issuer.issue("worker-1");
        WorkerJwt jwt = verifier.verify(issued.token());

        assertThat(jwt.workerId()).isEqualTo("worker-1");
        assertThat(jwt.jti()).isEqualTo(issued.jti());
        assertThat(jwt.expiresAt()).isCloseTo(
            issued.expiresAt(),
            org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS)
        );
    }

    @Test
    void revokedTokenRejected() throws Exception {
        WorkerJwtIssuer.IssuedWorkerJwt issued = issuer.issue("worker-1");
        when(denylist.isRevoked(issued.jti())).thenReturn(true);

        assertThatThrownBy(() -> verifier.verify(issued.token()))
            .isInstanceOf(WorkerJwtInvalidException.class)
            .hasMessageContaining("revoked");
    }

    @Test
    void unknownKidRejected() {
        WorkerSigningKey other = WorkerSigningKey.generateEphemeral("foreign-kid");
        String token = JWT.create()
            .withKeyId("foreign-kid")
            .withIssuer("hephaestus-test")
            .withSubject("worker-1")
            .withJWTId("j")
            .withIssuedAt(Instant.now())
            .withExpiresAt(Instant.now().plusSeconds(60))
            .sign(Algorithm.RSA256(other.publicKey(), other.privateKey()));

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(WorkerJwtInvalidException.class)
            .hasMessageContaining("unknown kid");
    }

    @Test
    void missingKidRejected() {
        String token = JWT.create()
            .withIssuer("hephaestus-test")
            .withSubject("worker-1")
            .withJWTId("j")
            .withIssuedAt(Instant.now())
            .withExpiresAt(Instant.now().plusSeconds(60))
            .sign(Algorithm.RSA256(keyRing.active().publicKey(), keyRing.active().privateKey()));

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(WorkerJwtInvalidException.class)
            .hasMessageContaining("missing kid");
    }

    @Test
    void rotation_oldTokenStillVerifiesAfterAddingNewActiveKey() {
        String issuerName = "hephaestus-rot-test";
        WorkerSigningKey keyA = WorkerSigningKey.generateEphemeral("kid-A");
        WorkerSigningKey keyB = WorkerSigningKey.generateEphemeral("kid-B");

        WorkerTokenProperties propsBefore = new WorkerTokenProperties(
            issuerName, "hephaestus-worker", Duration.ofMinutes(15), "register", List.of(toEntry(keyA)), "kid-A", null
        );
        String tokenSignedByA = new WorkerJwtIssuer(WorkerKeyRing.fromConfig(propsBefore), propsBefore)
            .issue("worker-1").token();

        WorkerTokenProperties propsAfter = new WorkerTokenProperties(
            issuerName, "hephaestus-worker", Duration.ofMinutes(15), "register", List.of(toEntry(keyA), toEntry(keyB)), "kid-B", null
        );
        WorkerJwtVerifier verifierAfter = new JavaJwtWorkerJwtVerifier(
            WorkerKeyRing.fromConfig(propsAfter), propsAfter, denylist, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
        String tokenSignedByB = new WorkerJwtIssuer(WorkerKeyRing.fromConfig(propsAfter), propsAfter)
            .issue("worker-2").token();

        assertThatNoException().isThrownBy(() -> verifierAfter.verify(tokenSignedByA));
        assertThatNoException().isThrownBy(() -> verifierAfter.verify(tokenSignedByB));

        WorkerTokenProperties propsDropA = new WorkerTokenProperties(
            issuerName, "hephaestus-worker", Duration.ofMinutes(15), "register", List.of(toEntry(keyB)), "kid-B", null
        );
        WorkerJwtVerifier verifierDropA = new JavaJwtWorkerJwtVerifier(
            WorkerKeyRing.fromConfig(propsDropA), propsDropA, denylist, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
        assertThatThrownBy(() -> verifierDropA.verify(tokenSignedByA))
            .isInstanceOf(WorkerJwtInvalidException.class)
            .hasMessageContaining("unknown kid");
        assertThatNoException().isThrownBy(() -> verifierDropA.verify(tokenSignedByB));
    }

    @ParameterizedTest(name = "fromConfig rejects {0}")
    @MethodSource("invalidRings")
    void fromConfigRejectsInvalidRings(String label, WorkerTokenProperties props, String expectedMessage) {
        assertThatThrownBy(() -> WorkerKeyRing.fromConfig(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> invalidRings() {
        WorkerSigningKey k = WorkerSigningKey.generateEphemeral("only-kid");
        return Stream.of(
            Arguments.of(
                "duplicate kid",
                new WorkerTokenProperties("iss", "aud", Duration.ofMinutes(15), "r", List.of(toEntry(k), toEntry(k)), null, null),
                "Duplicate kid"
            ),
            Arguments.of(
                "active-kid not in ring",
                new WorkerTokenProperties("iss", "aud", Duration.ofMinutes(15), "r", List.of(toEntry(k)), "missing-kid", null),
                "active-kid"
            )
        );
    }

    @Test
    void legacySingleKeyConfigPromotesToRing() {
        WorkerSigningKey k = WorkerSigningKey.generateEphemeral("scratch");
        WorkerTokenProperties legacy = new WorkerTokenProperties(
            "iss", "aud", Duration.ofMinutes(15), "r", List.of(), null, toPemPkcs8(k.privateKey())
        );
        WorkerKeyRing ring = WorkerKeyRing.fromConfig(legacy);
        assertThat(ring.size()).isEqualTo(1);
        assertThat(ring.active().kid()).isEqualTo("default");
    }

    @Test
    void propertiesToStringRedactsSecrets() {
        WorkerTokenProperties props = new WorkerTokenProperties(
            "iss", "aud", Duration.ofMinutes(5), "the-secret", List.of(), null, "the-pem"
        );
        String dumped = props.toString();
        assertThat(dumped).doesNotContain("the-secret");
        assertThat(dumped).doesNotContain("the-pem");
        assertThat(dumped).contains("<redacted>");
    }

    private static WorkerTokenProperties.KeyEntry toEntry(WorkerSigningKey key) {
        return new WorkerTokenProperties.KeyEntry(key.kid(), toPemPkcs8(key.privateKey()));
    }

    private static String toPemPkcs8(java.security.interfaces.RSAPrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(key.getEncoded()) +
            "\n-----END PRIVATE KEY-----\n";
    }
}

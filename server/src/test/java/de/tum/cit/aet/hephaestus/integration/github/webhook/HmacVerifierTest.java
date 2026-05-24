package de.tum.cit.aet.hephaestus.integration.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacVerifierTest extends BaseUnitTest {

    private static final String SECRET = "test-secret";
    private static final byte[] PAYLOAD = "{\"test\": \"data\"}".getBytes(StandardCharsets.UTF_8);

    private static String hmacHex(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void verifiesValidSha256() throws Exception {
        assertThat(HmacVerifier.verify("sha256=" + hmacHex(SECRET, PAYLOAD), SECRET, PAYLOAD)).isTrue();
    }

    @Test
    void rejectsInvalidSha256() {
        assertThat(HmacVerifier.verify("sha256=invalid1234567890", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsSha1Header() {
        // SHA-1 was a documented downgrade primitive — verifier intentionally rejects it.
        assertThat(HmacVerifier.verify("sha1=abc123", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsEmptyOrNullSignature() {
        assertThat(HmacVerifier.verify(null, SECRET, PAYLOAD)).isFalse();
        assertThat(HmacVerifier.verify("", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsEmptySecret() {
        assertThat(HmacVerifier.verify("sha256=abc", "", PAYLOAD)).isFalse();
    }

    @Test
    void rejectsUnknownAlgorithm() {
        assertThat(HmacVerifier.verify("md5=abc123", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsTruncatedSignature() throws Exception {
        String fullHex = hmacHex(SECRET, PAYLOAD);
        assertThat(HmacVerifier.verify("sha256=" + fullHex.substring(0, 32), SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsPaddedSignature() throws Exception {
        assertThat(HmacVerifier.verify("sha256=" + hmacHex(SECRET, PAYLOAD) + "extra", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsPrefixOnly() {
        assertThat(HmacVerifier.verify("sha256=", SECRET, PAYLOAD)).isFalse();
    }

    @Test
    void rejectsWhitespacePaddedSignature() throws Exception {
        String signature = "sha256=" + hmacHex(SECRET, PAYLOAD);
        assertThat(HmacVerifier.verify(" " + signature, SECRET, PAYLOAD)).isFalse();
        assertThat(HmacVerifier.verify(signature + " ", SECRET, PAYLOAD)).isFalse();
    }
}

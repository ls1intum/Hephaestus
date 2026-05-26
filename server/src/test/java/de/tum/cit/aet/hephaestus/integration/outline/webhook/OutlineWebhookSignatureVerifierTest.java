package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.WebhookRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OutlineWebhookSignatureVerifier")
class OutlineWebhookSignatureVerifierTest extends BaseUnitTest {

    private static final byte[] SECRET = "outline-subscription-secret-xyz".getBytes(StandardCharsets.UTF_8);

    /** Anonymous subclass returns a fixed secret without dragging in the subscription store. */
    private OutlineWebhookSecretSource secretSourceReturning(Optional<byte[]> secret) {
        return new OutlineWebhookSecretSource() {
            @Override
            public Optional<byte[]> getSecret(SecretLookup lookup) {
                return secret;
            }
        };
    }

    private static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validSignatureVerifies() {
        byte[] body = "{\"event\":\"documents.create\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("Outline-Signature", sign(body));
        WebhookRequest req = new WebhookRequest(body, headers);

        VerificationResult result = new OutlineWebhookSignatureVerifier(
            secretSourceReturning(Optional.of(SECRET))
        ).verify(req);

        assertThat(result).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void badSignatureIsInvalid() {
        byte[] body = "{\"event\":\"documents.create\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("Outline-Signature", "deadbeef");
        WebhookRequest req = new WebhookRequest(body, headers);

        VerificationResult result = new OutlineWebhookSignatureVerifier(
            secretSourceReturning(Optional.of(SECRET))
        ).verify(req);

        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).contains("mismatch");
    }

    @Test
    void missingHeaderIsMissingSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        WebhookRequest req = new WebhookRequest(body, Map.of());

        VerificationResult result = new OutlineWebhookSignatureVerifier(
            secretSourceReturning(Optional.of(SECRET))
        ).verify(req);

        assertThat(result).isInstanceOf(VerificationResult.MissingSignature.class);
    }

    @Test
    void emptySecretSourceIsMissingSignatureUntilSubscriptionWired() {
        byte[] body = "{\"event\":\"documents.create\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("Outline-Signature", "anything");
        WebhookRequest req = new WebhookRequest(body, headers);

        VerificationResult result = new OutlineWebhookSignatureVerifier(secretSourceReturning(Optional.empty())).verify(
            req
        );

        // Distinct from Slack's "Invalid(unconfigured)": Outline secrets are per-subscription,
        // so "no secret captured yet" is operationally the same as "unknown subscription".
        assertThat(result).isInstanceOf(VerificationResult.MissingSignature.class);
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        byte[] body = "{\"event\":\"documents.create\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("outline-signature", sign(body));
        WebhookRequest req = new WebhookRequest(body, headers);

        VerificationResult result = new OutlineWebhookSignatureVerifier(
            secretSourceReturning(Optional.of(SECRET))
        ).verify(req);

        assertThat(result).isInstanceOf(VerificationResult.Verified.class);
    }

    /** Sanity: WebhookSecretSource interface contract — this is a real SubscriptionScope source. */
    @Test
    void secretSourceUsesSubscriptionScope() {
        assertThat(new OutlineWebhookSecretSource().scope()).isEqualTo(WebhookSecretSource.Scope.SUBSCRIPTION);
    }
}

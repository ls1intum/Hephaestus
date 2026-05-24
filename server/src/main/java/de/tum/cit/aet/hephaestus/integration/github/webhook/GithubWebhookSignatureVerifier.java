package de.tum.cit.aet.hephaestus.integration.github.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link WebhookSignatureVerifier}: validates the
 * {@code X-Hub-Signature-256} HMAC-SHA256 against the raw request body using the
 * APP_GLOBAL shared secret resolved by {@link GithubWebhookSecretSource}.
 *
 * <p>This is the integration-framework-side mirror of the legacy
 * {@code gitprovider.webhook.github.HmacVerifier}; the algorithm is identical
 * (constant-time {@link MessageDigest#isEqual(byte[], byte[])} comparison, SHA-1 path
 * intentionally rejected — downgrade-resistant). Duplicating the ~20-line crypto
 * routine rather than reaching into the legacy package keeps the integration module
 * free of {@code gitprovider} dependencies during the C13 migration window — both
 * verifiers must stay green until the legacy GitHubWebhookController is retired.
 */
@Component
public class GithubWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookSignatureVerifier.class);

    private static final String HEADER_SIGNATURE_256 = "X-Hub-Signature-256";
    private static final String SHA256_PREFIX = "sha256=";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final WebhookSecretSource secretSource;

    public GithubWebhookSignatureVerifier(GithubWebhookSecretSource secretSource) {
        this.secretSource = secretSource;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public VerificationResult verify(WebhookRequest request) {
        String signature = headerCaseInsensitive(request, HEADER_SIGNATURE_256);
        if (signature == null || signature.isBlank()) {
            return new VerificationResult.MissingSignature();
        }
        if (!signature.startsWith(SHA256_PREFIX)) {
            return new VerificationResult.Invalid("unsupported-signature-algo");
        }

        Optional<byte[]> secret = secretSource.getSecret(
            new SecretLookup(request.workspaceId(), request.subscriptionId(), request.headers())
        );
        if (secret.isEmpty()) {
            log.warn("GitHub webhook rejected: shared secret not configured");
            return new VerificationResult.Invalid("missing-secret");
        }

        byte[] computed;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.get(), HMAC_SHA256));
            computed = mac.doFinal(request.body());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.warn("GitHub webhook HMAC init failed: {}", e.toString());
            return new VerificationResult.Invalid("hmac-init-failed");
        }

        byte[] expected = (SHA256_PREFIX + HexFormat.of().formatHex(computed)).getBytes(StandardCharsets.UTF_8);
        byte[] header = signature.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(header, expected)) {
            return new VerificationResult.Verified();
        }
        return new VerificationResult.Invalid("signature-mismatch");
    }

    private static String headerCaseInsensitive(WebhookRequest request, String name) {
        String direct = request.headers().get(name);
        if (direct != null) return direct;
        for (var entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

package de.tum.cit.aet.hephaestus.integration.gitlab.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * App-global GitLab webhook secret source.
 *
 * <p>First cut for #1198: returns the same shared {@code hephaestus.webhook.secret}
 * for both verification modes. The plaintext path byte-compares this value directly;
 * the {@code whsec_*} path interprets it as the {@code whsec_<b64>} signing secret
 * if it begins with {@code whsec_}, and treats it as opaque bytes otherwise.
 *
 * <p>TODO(#1198 follow-up): per-workspace {@code whsec_*} secrets live in each
 * Connection's {@link de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig.GitLabConfig}
 * blob, decrypted via the per-Connection credential converter. Switching this source
 * to {@code Scope.WORKSPACE} requires that converter, which ships in the credential
 * provider follow-up. Until then, multi-tenant setups using Standard Webhooks must
 * share one signing secret cluster-wide — acceptable for the migration first cut,
 * not acceptable long-term.
 */
@Component
public class GitlabWebhookSecretSource implements WebhookSecretSource {

    private final byte[] sharedSecretBytes;

    public GitlabWebhookSecretSource(@Value("${hephaestus.webhook.secret:}") String sharedSecret) {
        this.sharedSecretBytes = sharedSecret == null || sharedSecret.isBlank()
            ? new byte[0]
            : sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public Scope scope() {
        // APP_GLOBAL today. When per-workspace whsec_* lands, this flips to WORKSPACE
        // and getSecret() reads from ConnectionService via SecretLookup.workspaceId.
        return Scope.APP_GLOBAL;
    }

    @Override
    public Optional<byte[]> getSecret(SecretLookup lookup) {
        if (sharedSecretBytes.length == 0) {
            return Optional.empty();
        }
        // Defensive copy: callers MUST NOT be able to mutate our cached secret.
        byte[] copy = new byte[sharedSecretBytes.length];
        System.arraycopy(sharedSecretBytes, 0, copy, 0, sharedSecretBytes.length);
        return Optional.of(copy);
    }
}

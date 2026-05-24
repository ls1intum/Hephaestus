package de.tum.cit.aet.hephaestus.integration.gitlab.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GitLab integration manifest. {@code GitlabWebhookSignatureVerifier} runs dual-mode
 * (legacy plaintext {@code X-Gitlab-Token} + GitLab 19.0+ HMAC {@code whsec_*}).
 *
 * <p>Feedback-delivery / inline-finding / approval capabilities are gated on the legacy
 * {@code hephaestus.gitlab.enabled} flag — the underlying GraphQL provider and the
 * channel beans share that flag, and the bootstrap demands matching SPI beans for any
 * declared capability.
 *
 * <p>No {@code RATE_LIMITED}: GitLab has no per-kind {@code RateLimitTracker} impl.
 * No {@code SCOPE_CHANGES}: GitLab has no install/uninstall/scope webhooks, so
 * {@code GitlabLifecycleListener.onScopeChanged} would never fire — declaring the
 * capability would lie to the UI's practice-gating check.
 */
@Component
@ConditionalOnProperty(
    name = "hephaestus.integration.gitlab.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GitLabManifest implements IntegrationManifest {

    private final boolean gitlabStackEnabled;

    public GitLabManifest(@Value("${hephaestus.gitlab.enabled:false}") boolean gitlabStackEnabled) {
        this.gitlabStackEnabled = gitlabStackEnabled;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public String displayName() {
        return "GitLab";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        EnumSet<Capability> capabilities = EnumSet.of(Capability.WEBHOOK_INGEST);
        if (gitlabStackEnabled) {
            // GraphQL provider + channel beans only load when hephaestus.gitlab.enabled=true.
            capabilities.add(Capability.FEEDBACK_DELIVERY);
            capabilities.add(Capability.INLINE_FINDINGS);
            capabilities.add(Capability.APPROVAL_WORKFLOW);
        }
        return Set.copyOf(capabilities);
    }
}

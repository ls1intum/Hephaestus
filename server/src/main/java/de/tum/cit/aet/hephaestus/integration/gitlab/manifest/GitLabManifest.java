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
 * GitLab integration manifest.
 *
 * <p>{@code GitlabWebhookSignatureVerifier} ships in dual-mode (legacy plaintext
 * {@code X-Gitlab-Token} + GitLab 19.0+ HMAC {@code whsec_*}).
 *
 * <p>{@code matchIfMissing = true}: with the GitLab SPI beans wired
 * (signature verifier, secret source, subject deriver/parser, credential provider,
 * connection strategy, lifecycle listener) the manifest opts into validation by
 * default.
 *
 * <p>The feedback-delivery / inline-finding / approval capabilities are gated on the
 * legacy {@code hephaestus.gitlab.enabled} flag because the underlying
 * {@link de.tum.cit.aet.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider}
 * is also gated on that flag — and so are the channel beans under
 * {@code integration/gitlab/feedback/}
 * ({@link de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabFeedbackChannel},
 * {@link de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabInlineFindingChannel},
 * {@link de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabApprovalChannel}).
 * The manifest mirrors that gating so {@code IntegrationFrameworkBootstrap} won't
 * demand channel beans on deployments where the GitLab GraphQL stack is disabled.
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
        // Each entry below MUST correspond to a wired SPI bean under
        // integration/gitlab/. IntegrationFrameworkBootstrap fails startup otherwise.
        EnumSet<Capability> capabilities = EnumSet.of(
            Capability.WEBHOOK_INGEST,   // GitlabWebhookSignatureVerifier + SecretSource + SubjectKeyDeriver + SubjectParser
            Capability.RATE_LIMITED,     // generic RateLimitTracker (any-kind) — see bootstrap rule
            Capability.SCOPE_CHANGES     // GitlabLifecycleListener.onScopeChanged stub (real wiring in follow-up)
        );
        if (gitlabStackEnabled) {
            // GitLabGraphQlClientProvider + the three channel beans only load when
            // hephaestus.gitlab.enabled=true; declare matching capabilities only then.
            capabilities.add(Capability.FEEDBACK_DELIVERY);
            capabilities.add(Capability.INLINE_FINDINGS);
            capabilities.add(Capability.APPROVAL_WORKFLOW);
        }
        return Set.copyOf(capabilities);
    }
}

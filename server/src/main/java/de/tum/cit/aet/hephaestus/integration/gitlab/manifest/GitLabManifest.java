package de.tum.cit.aet.hephaestus.integration.gitlab.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GitLab integration manifest.
 *
 * <p>{@code GitlabWebhookSignatureVerifier} ships in dual-mode (legacy plaintext
 * {@code X-Gitlab-Token} + GitLab 19.0+ HMAC {@code whsec_*}).
 *
 * <p>{@code matchIfMissing = true}: with the GitLab SPI beans now wired
 * (signature verifier, secret source, subject deriver/parser, credential provider,
 * connection strategy, lifecycle listener) the manifest opts into validation by
 * default. The narrowed capability set lists ONLY what those beans actually back —
 * additional capabilities ({@code FEEDBACK_DELIVERY}, {@code INLINE_FINDINGS},
 * {@code APPROVAL_WORKFLOW}, {@code GIT_CONTENT_ACCESS}, {@code BACKFILL_SYNC},
 * {@code STATUS_REPORTING}) re-add as their SPI implementations land in follow-up
 * commits.
 */
@Component
@ConditionalOnProperty(
    name = "hephaestus.integration.gitlab.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GitLabManifest implements IntegrationManifest {

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
        // integration/gitlab/. IntegrationFrameworkBootstrap fails startup
        // otherwise.
        return Set.of(
            Capability.WEBHOOK_INGEST,   // GitlabWebhookSignatureVerifier + SecretSource + SubjectKeyDeriver + SubjectParser
            Capability.RATE_LIMITED,     // generic RateLimitTracker (any-kind) — see bootstrap rule
            Capability.SCOPE_CHANGES     // GitlabLifecycleListener.onScopeChanged stub (real wiring in follow-up)
        );
    }
}

package de.tum.cit.aet.hephaestus.integration.github.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GitHub manifest. Enabled by default ({@code matchIfMissing = true}) since the
 * #1198 first cut wires the universal SPI surface (webhook verifier, secret source,
 * subject deriver/parser, credential provider, token refresher) under
 * {@code integration/github/}.
 *
 * <p>The feedback-delivery / inline-finding / approval capabilities are now backed by
 * the channel beans under {@code integration/github/feedback/}
 * ({@link de.tum.cit.aet.hephaestus.integration.github.feedback.GithubFeedbackChannel},
 * {@link de.tum.cit.aet.hephaestus.integration.github.feedback.GithubInlineFindingChannel},
 * {@link de.tum.cit.aet.hephaestus.integration.github.feedback.GithubApprovalChannel}).
 * Capabilities still pending C13 migration ({@code GIT_CONTENT_ACCESS},
 * {@code BACKFILL_SYNC}, {@code STATUS_REPORTING}) re-add as the corresponding SPI
 * beans land.
 *
 * <p>Disable for tests / fixtures that intentionally exercise a degraded boot by
 * setting {@code hephaestus.integration.github.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.github.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubManifest implements IntegrationManifest {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        return Set.of(
            Capability.WEBHOOK_INGEST,
            Capability.TOKEN_REFRESH,
            Capability.FEEDBACK_DELIVERY,
            Capability.INLINE_FINDINGS,
            Capability.APPROVAL_WORKFLOW
        );
    }
}

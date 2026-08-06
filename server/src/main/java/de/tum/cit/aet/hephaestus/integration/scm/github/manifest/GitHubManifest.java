package de.tum.cit.aet.hephaestus.integration.scm.github.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for GitHub. Disable with
 *  {@code hephaestus.integration.github.enabled=false}. */
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

    /**
     * GitHub carries the shared SCM domain in full: every signal either descriptor declares as
     * webhook-produced has a GitHub event behind it, which is why it is the vendor the vocabulary was
     * shaped against. The manual review-request signals are absent from both sets because no ingested
     * event raises them — somebody asks for those by hand.
     */
    @Override
    public ReviewContribution reviewContribution() {
        return new ReviewContribution(
            Set.of(ScmSignals.PULL_REQUEST, ScmSignals.ISSUE),
            Map.of(
                ScmSignals.PULL_REQUEST,
                Set.of(
                    ScmSignals.PULL_REQUEST_OPENED,
                    ScmSignals.PULL_REQUEST_READY,
                    ScmSignals.PULL_REQUEST_SYNCHRONIZED,
                    ScmSignals.PULL_REQUEST_REVIEWED,
                    ScmSignals.PULL_REQUEST_MERGED,
                    ScmSignals.PULL_REQUEST_CLOSED
                ),
                ScmSignals.ISSUE,
                Set.of(ScmSignals.ISSUE_OPENED, ScmSignals.ISSUE_LABELED, ScmSignals.ISSUE_CLOSED)
            ),
            Map.of(
                ScmSignals.PULL_REQUEST,
                Set.of(FeedbackLane.IN_CONTEXT_SUMMARY, FeedbackLane.IN_CONTEXT_INLINE),
                ScmSignals.ISSUE,
                Set.of(FeedbackLane.IN_CONTEXT_SUMMARY)
            )
        );
    }
}

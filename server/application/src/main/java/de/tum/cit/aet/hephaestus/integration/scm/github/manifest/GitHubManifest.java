package de.tum.cit.aet.hephaestus.integration.scm.github.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-kind capability declaration for GitHub. Disable with {@code
 * hephaestus.integration.github.enabled=false}.
 */
@Component
public class GitHubManifest implements IntegrationManifest {

    private final boolean githubEnabled;

    public GitHubManifest(@Value("${hephaestus.integration.github.enabled:true}") boolean githubEnabled) {
        this.githubEnabled = githubEnabled;
    }

    @Override
    public boolean enabled() {
        return githubEnabled;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public String reviewCommitFor(@Nullable String recordedCommit, @Nullable String currentHead) {
        if (recordedCommit == null || recordedCommit.isBlank() || currentHead == null || currentHead.isBlank()) {
            return "UNKNOWN";
        }
        return recordedCommit.equals(currentHead) ? "CURRENT_HEAD" : "OTHER_COMMIT";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        return Set.of(
                Capability.WEBHOOK_INGEST,
                Capability.TOKEN_REFRESH,
                Capability.FEEDBACK_DELIVERY,
                Capability.INLINE_FEEDBACK,
                Capability.APPROVAL_WORKFLOW);
    }

    /**
     * GitHub carries the shared SCM domain in full: every signal a descriptor declares as
     * webhook-produced has a GitHub event behind it. The manual review-request signals are absent
     * because no ingested event raises them — somebody asks for those by hand.
     */
    @Override
    public ReviewContribution reviewContribution() {
        return new ReviewContribution(
                Set.of(ScmItemEvents.PULL_REQUEST, ScmItemEvents.ISSUE),
                Map.of(
                        ScmItemEvents.PULL_REQUEST,
                        Set.of(
                                ScmItemEvents.PULL_REQUEST_OPENED,
                                ScmItemEvents.PULL_REQUEST_READY,
                                ScmItemEvents.PULL_REQUEST_SYNCHRONIZED,
                                ScmItemEvents.PULL_REQUEST_EDITED,
                                ScmItemEvents.PULL_REQUEST_LINKED_ISSUE_UPDATED,
                                ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED,
                                ScmItemEvents.PULL_REQUEST_MERGED,
                                ScmItemEvents.PULL_REQUEST_CLOSED),
                        ScmItemEvents.ISSUE,
                        Set.of(ScmItemEvents.ISSUE_OPENED, ScmItemEvents.ISSUE_UPDATED, ScmItemEvents.ISSUE_CLOSED)),
                Map.of(
                        ScmItemEvents.PULL_REQUEST,
                        Set.of(CopyCapability.SUMMARY, CopyCapability.LINE_NOTE),
                        ScmItemEvents.ISSUE,
                        Set.of(CopyCapability.SUMMARY)));
    }
}

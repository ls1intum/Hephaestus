package de.tum.cit.aet.hephaestus.integration.scm.gitlab.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitLab integration manifest.
 *
 * <p>Feedback-delivery, inline-feedback and approval capabilities are gated on {@code
 * hephaestus.integration.gitlab.enabled}, the same flag the GraphQL provider and channel beans
 * require — the bootstrap demands matching SPI beans for every declared capability. GitLab is
 * opt-in (default off), but the manifest bean stays registered either way so a workspace can be
 * told that connecting GitLab would wake a dormant practice.
 *
 * <p>No {@code RATE_LIMITED}: no per-kind {@code RateLimitTracker}. No {@code SCOPE_CHANGES}:
 * GitLab has no install/uninstall/scope webhooks to fire it.
 */
@Component
public class GitLabManifest implements IntegrationManifest {

    private final boolean gitlabStackEnabled;

    public GitLabManifest(@Value("${hephaestus.integration.gitlab.enabled:false}") boolean gitlabStackEnabled) {
        this.gitlabStackEnabled = gitlabStackEnabled;
    }

    @Override
    public boolean enabled() {
        return gitlabStackEnabled;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public String displayName() {
        return "GitLab";
    }

    /** A merge hook often names no merger; the read after it, or a sync, does. */
    @Override
    public Set<AboutRole> rolesNamedAfterTheOccasion() {
        return Set.of(AboutRole.MERGER);
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        EnumSet<Capability> capabilities = EnumSet.of(Capability.WEBHOOK_INGEST);
        if (gitlabStackEnabled) {
            capabilities.add(Capability.FEEDBACK_DELIVERY);
            capabilities.add(Capability.INLINE_FEEDBACK);
            capabilities.add(Capability.APPROVAL_WORKFLOW);
        }
        return Set.copyOf(capabilities);
    }

    /**
     * Delivery lanes track the same enablement flag as the channel beans; claiming a lane whose
     * {@code SummaryWriter} is not wired fails at boot.
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
                gitlabStackEnabled
                        ? Map.of(
                                ScmItemEvents.PULL_REQUEST,
                                Set.of(CopyCapability.SUMMARY, CopyCapability.LINE_NOTE),
                                ScmItemEvents.ISSUE,
                                Set.of(CopyCapability.SUMMARY))
                        : Map.of());
    }
}

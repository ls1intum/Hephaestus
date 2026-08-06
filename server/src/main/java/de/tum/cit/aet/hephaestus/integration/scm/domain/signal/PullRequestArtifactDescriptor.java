package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITHUB_PULL_REQUEST;
import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITHUB_PULL_REQUEST_REVIEW;
import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITLAB_MERGE_REQUEST;
import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITLAB_NOTE;
import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.declare;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The pull/merge request as a reviewable artifact.
 *
 * <p>Declared here, in the shared SCM domain, rather than in either vendor package: {@code PullRequest}
 * is one entity that both processors write to, so there is exactly one notion of what a pull request is
 * and exactly one vocabulary for what happens to it. A vendor then declares which part of that
 * vocabulary it can actually deliver.
 *
 * <p>Unconditional on purpose — the domain exists whether or not GitHub or GitLab is enabled, and a
 * descriptor that came and went with a feature flag would make the vocabulary itself configuration.
 */
@Component
public class PullRequestArtifactDescriptor implements ArtifactDescriptor {

    private static final List<Signal> SIGNALS = List.of(
        declare(ScmSignals.PULL_REQUEST_OPENED, "Opened", Set.of(GITHUB_PULL_REQUEST, GITLAB_MERGE_REQUEST)),
        declare(
            ScmSignals.PULL_REQUEST_READY,
            "Marked ready for review",
            Set.of(GITHUB_PULL_REQUEST, GITLAB_MERGE_REQUEST)
        ),
        // GitHub only, and writing that down is the point. GitLab's webhook path emits no synchronize
        // event at all, so a practice watching "new commits are pushed" is silent on GitLab — until now
        // indistinguishable from a workspace where nobody pushes.
        declare(ScmSignals.PULL_REQUEST_SYNCHRONIZED, "New commits pushed", Set.of(GITHUB_PULL_REQUEST)),
        // Three sources for one signal: GitHub has a dedicated review event, GitLab splits the same fact
        // across an approval on the merge request and a review note.
        declare(
            ScmSignals.PULL_REQUEST_REVIEWED,
            "Review submitted",
            Set.of(GITHUB_PULL_REQUEST_REVIEW, GITLAB_MERGE_REQUEST, GITLAB_NOTE)
        ),
        declare(ScmSignals.PULL_REQUEST_MERGED, "Merged", Set.of(GITHUB_PULL_REQUEST, GITLAB_MERGE_REQUEST)),
        declare(
            ScmSignals.PULL_REQUEST_CLOSED,
            "Closed without merging",
            Set.of(GITHUB_PULL_REQUEST, GITLAB_MERGE_REQUEST)
        ),
        // No ingested event raises this one: it is somebody asking for a review by hand. Declaring the
        // empty provenance is what stops a vendor from claiming it can raise it.
        declare(ScmSignals.PULL_REQUEST_REVIEW_REQUESTED, "Review requested by hand", Set.of())
    );

    @Override
    public ArtifactKind kind() {
        return ScmSignals.PULL_REQUEST;
    }

    @Override
    public String displayName() {
        return "Pull or merge request";
    }

    @Override
    public List<Signal> signals() {
        return SIGNALS;
    }

    @Override
    public Set<ActorRole> roles() {
        return Set.of(ActorRole.AUTHOR, ActorRole.ASSIGNEE, ActorRole.REVIEWER);
    }

    @Override
    public Set<FeedbackLane> lanes() {
        // The only artifact carrying a diff, and therefore the only one that can take a positional note.
        return Set.of(FeedbackLane.IN_CONTEXT_SUMMARY, FeedbackLane.IN_CONTEXT_INLINE);
    }

    @Override
    public boolean reviewable() {
        return true;
    }
}

package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITHUB_ISSUES;
import static de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmEventSources.GITLAB_ISSUE;
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
 * The issue as a reviewable artifact.
 *
 * <p>Deliberately not a pull request with fewer fields. An issue has no diff, so it has no inline lane
 * and no reviewer relation to attribute anything to; everything a practice can say about it is about
 * what a person wrote, which is also why every one of its signals keys on a content digest rather than
 * a commit.
 */
@Component
public class IssueArtifactDescriptor implements ArtifactDescriptor {

    private static final List<Signal> SIGNALS = List.of(
        declare(ScmSignals.ISSUE_OPENED, "Opened", Set.of(GITHUB_ISSUES, GITLAB_ISSUE)),
        // GitLab has no native "labeled" action; its processor derives one per newly added label from the
        // update event, so both vendors really do raise this and the provenance is honest.
        declare(ScmSignals.ISSUE_LABELED, "Labeled", Set.of(GITHUB_ISSUES, GITLAB_ISSUE)),
        declare(ScmSignals.ISSUE_CLOSED, "Closed", Set.of(GITHUB_ISSUES, GITLAB_ISSUE)),
        declare(ScmSignals.ISSUE_REVIEW_REQUESTED, "Review requested by hand", Set.of())
    );

    @Override
    public ArtifactKind kind() {
        return ScmSignals.ISSUE;
    }

    @Override
    public String displayName() {
        return "Issue";
    }

    @Override
    public List<Signal> signals() {
        return SIGNALS;
    }

    @Override
    public Set<ActorRole> roles() {
        return Set.of(ActorRole.AUTHOR, ActorRole.ASSIGNEE);
    }

    @Override
    public Set<FeedbackLane> lanes() {
        return Set.of(FeedbackLane.IN_CONTEXT_SUMMARY);
    }

    @Override
    public boolean reviewable() {
        return true;
    }
}

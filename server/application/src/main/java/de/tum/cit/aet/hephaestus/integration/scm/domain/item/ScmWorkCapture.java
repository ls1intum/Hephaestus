package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.GeneralReviewCommentContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.IssueContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ReviewThreadContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCaptureContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScmWorkCapture {
    private final PullRequestContentSource pullContent;
    private final IssueContentSource issueContent;
    private final GeneralReviewCommentContentSource generalComments;
    private final ReviewThreadContentSource threads;
    private final LinkedWorkItemContentSource linked;
    private final RepositoryTreeContentSource repositoryTree;

    CaptureContribution capture(ScmCaptureContext context) {
        List<CaptureContribution> contributions = new ArrayList<>();
        try {
            if (context.pinned().item().kind().equals(ScmItemEvents.ISSUE))
                contributions.add(
                        issueContent.capture(context, context.request().sources()));
            else {
                contributions.add(pullContent.capture(context, context.request().sources()));
                contributions.add(
                        generalComments.capture(context, context.request().sources()));
                contributions.add(threads.capture(context, context.request().sources()));
                contributions.add(linked.capture(context, context.request().sources()));
                contributions.add(
                        repositoryTree.capture(context, context.request().sources()));
            }
            return ScmCaptureContributions.combine(contributions);
        } catch (RuntimeException failure) {
            for (var contribution : contributions)
                if (contribution.cleanup() != null)
                    try {
                        contribution.cleanup().close();
                    } catch (Exception e) {
                        failure.addSuppressed(e);
                    }
            throw failure;
        }
    }
}

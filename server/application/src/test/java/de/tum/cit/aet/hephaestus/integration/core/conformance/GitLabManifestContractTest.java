package de.tum.cit.aet.hephaestus.integration.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.FileItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.IssueItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.PullRequestItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.PullRequestReviewItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.RepositoryItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.manifest.GitLabManifest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * GitLab through the shared acceptance suite.
 *
 * <p>The reason this suite exists in the shape it does: GitLab is opt-in and off by default, so
 * nothing on a normal instance — including CI's Spring contexts — ever validates its declaration.
 * Its manifest is constructed with the stack enabled, which is the only configuration in which the
 * bean exists at all.
 */
class GitLabManifestContractTest extends IntegrationManifestContractTest {

    @Test
    void shouldNotTreatGitLabsRecordedAssociationAsTheOriginallyApprovedCommit() {
        var head = "a".repeat(40);
        assertThat(manifest().reviewCommitFor(head, head)).isEqualTo("UNKNOWN");
        assertThat(manifest().reviewCommitFor("b".repeat(40), head)).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldDeclareSubmittedReviewEventsAndDeliveryOnTheirOwnItemKind() {
        var contribution = manifest().reviewContribution();
        assertThat(contribution.observes())
                .contains(ScmItemEvents.PULL_REQUEST_REVIEW, ScmItemEvents.REPOSITORY, ScmItemEvents.FILE);
        assertThat(contribution.raises())
                .containsEntry(ScmItemEvents.PULL_REQUEST_REVIEW, Set.of(ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED));
        assertThat(contribution.raises().get(ScmItemEvents.PULL_REQUEST))
                .doesNotContain(ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED);
        assertThat(contribution.delivers())
                .containsEntry(
                        ScmItemEvents.PULL_REQUEST_REVIEW, Set.of(CopyCapability.SUMMARY, CopyCapability.LINE_NOTE));
    }

    @Override
    protected IntegrationManifest manifest() {
        return new GitLabManifest(true);
    }

    @Override
    protected List<ItemDescriptor> descriptors() {
        return List.of(
                new PullRequestItemDescriptor(),
                new IssueItemDescriptor(),
                new PullRequestReviewItemDescriptor(),
                new RepositoryItemDescriptor(),
                new FileItemDescriptor());
    }
}

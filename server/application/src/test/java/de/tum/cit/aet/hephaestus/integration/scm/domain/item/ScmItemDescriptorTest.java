package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ScmItemDescriptorTest extends BaseUnitTest {
    @Test
    void shouldKeepSubmittedReviewOnItsOwnKind() {
        var review = new PullRequestReviewItemDescriptor();
        assertThat(review.kind()).isEqualTo(ScmItemEvents.PULL_REQUEST_REVIEW);
        assertThat(review.roles()).containsExactly(AboutRole.REVIEWER);
        assertThat(review.events())
                .extracting(e -> e.name().value())
                .containsExactly("scm.pull_request_review.submitted");
        assertThat(new PullRequestItemDescriptor().events())
                .noneSatisfy(e -> assertThat(e.name().value()).contains("reviewed"));
    }

    @Test
    void shouldDeclareFilesAsRepositoryMembers() {
        assertThat(new RepositoryItemDescriptor().memberKinds()).contains(ScmItemEvents.FILE);
    }
}

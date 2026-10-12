package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ReviewTriggerTest extends BaseUnitTest {

    @Test
    void shouldNameTheOccasionOfAMomentAProviderReported() {
        assertThat(ReviewTrigger.of("scm.pull_request.opened", DiscoveredVia.EVENT))
                .isEqualTo(ReviewTrigger.OPENED);
        assertThat(ReviewTrigger.of("scm.pull_request.ready", DiscoveredVia.SYNC))
                .isEqualTo(ReviewTrigger.READY);
        assertThat(ReviewTrigger.of("scm.pull_request.synchronized", DiscoveredVia.EVENT))
                .isEqualTo(ReviewTrigger.PUSHED);
        assertThat(ReviewTrigger.of("scm.pull_request.merged", DiscoveredVia.EVENT))
                .isEqualTo(ReviewTrigger.MERGED);
        assertThat(ReviewTrigger.of("scm.pull_request.manual_review", DiscoveredVia.EVENT))
                .isEqualTo(ReviewTrigger.REQUESTED);
        assertThat(ReviewTrigger.of("chat.conversation_thread.settled", DiscoveredVia.EVENT))
                .isEqualTo(ReviewTrigger.SETTLED);
    }

    @Test
    void shouldNameHowTheMomentWasFoundBeforeItsOccasionWhenAPersonOrASweepStartedIt() {
        assertThat(ReviewTrigger.of("scm.pull_request.merged", DiscoveredVia.MANUAL))
                .isEqualTo(ReviewTrigger.REQUESTED);
        assertThat(ReviewTrigger.of("scm.pull_request.opened", DiscoveredVia.BACKFILL))
                .isEqualTo(ReviewTrigger.BACKFILL);
        assertThat(ReviewTrigger.of("scm.pull_request.opened", DiscoveredVia.SWEEP))
                .isEqualTo(ReviewTrigger.SWEEP);
    }

    @Test
    void shouldNameAMomentByItsOccasionWhoeverFoundIt() {
        assertThat(ReviewTrigger.moment("scm.pull_request.opened", DiscoveredVia.BACKFILL))
                .isEqualTo(ReviewTrigger.OPENED);
        assertThat(ReviewTrigger.moment("scm.pull_request.merged", DiscoveredVia.SWEEP))
                .isEqualTo(ReviewTrigger.MERGED);
        assertThat(ReviewTrigger.moment("scm.pull_request.opened", DiscoveredVia.MANUAL))
                .isEqualTo(ReviewTrigger.REQUESTED);
        assertThat(ReviewTrigger.moment("docs.document.archived", DiscoveredVia.EVENT))
                .isNull();
    }

    @Test
    void shouldNameNoTriggerForAnOccasionItDoesNotKnow() {
        assertThat(ReviewTrigger.of("docs.document.archived", DiscoveredVia.EVENT))
                .isNull();
    }
}

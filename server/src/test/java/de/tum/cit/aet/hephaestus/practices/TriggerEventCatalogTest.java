package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class TriggerEventCatalogTest extends BaseUnitTest {

    @Test
    void pullRequestFocus_carriesReviewLifecycleEvents() {
        var pr = TriggerEventCatalog.eligibleFor(WorkArtifact.PULL_REQUEST);
        assertThat(pr).contains(
            TriggerEventNames.PULL_REQUEST_CREATED,
            TriggerEventNames.PULL_REQUEST_READY,
            TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
            TriggerEventNames.REVIEW_SUBMITTED
        );
    }

    @Test
    void pullRequestFocus_carriesRetrospectiveMergedEvent() {
        var pr = TriggerEventCatalog.eligibleFor(WorkArtifact.PULL_REQUEST);
        assertThat(pr).contains(TriggerEventNames.PULL_REQUEST_MERGED);
    }

    @Test
    void pullRequestFocus_carriesRetrospectiveClosedEvent() {
        // The abandoned-PR outcome (wasMerged=false) is the contract-sensitive sibling of PULL_REQUEST_MERGED
        // (ScmDomainEvent.TriggerEventNames.PULL_REQUEST_CLOSED javadoc). Assert it explicitly so a regression
        // that drops the not-landed trigger from PULL_REQUEST_EVENTS fails loudly instead of only via the
        // transitive disjointness check.
        var pr = TriggerEventCatalog.eligibleFor(WorkArtifact.PULL_REQUEST);
        assertThat(pr).contains(TriggerEventNames.PULL_REQUEST_CLOSED);
    }

    @Test
    void issueFocus_carriesIssueEvents() {
        var issue = TriggerEventCatalog.eligibleFor(WorkArtifact.ISSUE);
        assertThat(issue).contains(TriggerEventNames.ISSUE_CREATED, TriggerEventNames.ISSUE_LABELED);
    }

    @Test
    void issueFocus_carriesRetrospectiveClosedEvent() {
        var issue = TriggerEventCatalog.eligibleFor(WorkArtifact.ISSUE);
        assertThat(issue).contains(TriggerEventNames.ISSUE_CLOSED);
    }

    @Test
    void focusSetsAreDisjoint_soACombinationCannotBeAccidentallyValidForBoth() {
        var pr = TriggerEventCatalog.eligibleFor(WorkArtifact.PULL_REQUEST);
        var issue = TriggerEventCatalog.eligibleFor(WorkArtifact.ISSUE);
        assertThat(pr).doesNotContainAnyElementsOf(issue);
    }
}

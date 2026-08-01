package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogLink;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A workspace's copies are never rewritten from above, so the only question worth answering is how
 * far each has drifted. It is read off three fingerprints, which is why it cannot go stale.
 */
class CatalogOriginTest extends BaseUnitTest {

    private static final String SLUG = "small-prs";

    @Test
    void aPracticeTheWorkspaceWroteItselfHasNoOrigin() {
        assertThat(CatalogOrigin.of(practice("Seed criteria", null), catalog("Seed criteria"))).isNull();
    }

    @Test
    void anUntouchedCopyOfWhatTheCatalogOffersIsInSync() {
        Practice copy = practice("Seed criteria", fingerprintOf("Seed criteria"));

        assertThat(CatalogOrigin.of(copy, catalog("Seed criteria")).link()).isEqualTo(CatalogLink.IN_SYNC);
    }

    @Test
    void aCopyTheWorkspaceChangedReadsAsEditedHere() {
        Practice copy = practice("Our own criteria", fingerprintOf("Seed criteria"));

        assertThat(CatalogOrigin.of(copy, catalog("Seed criteria")).link()).isEqualTo(CatalogLink.LOCALLY_EDITED);
    }

    @Test
    void anUntouchedCopyWhoseCatalogEntryMovedOnReadsAsUpdateAvailable() {
        Practice copy = practice("Seed criteria", fingerprintOf("Seed criteria"));

        assertThat(CatalogOrigin.of(copy, catalog("Instance criteria")).link()).isEqualTo(CatalogLink.UPDATE_AVAILABLE);
    }

    @Test
    void aCopyEditedAwayAndBackIsInSyncAgain() {
        Practice copy = practice("Our own criteria", fingerprintOf("Seed criteria"));
        assertThat(CatalogOrigin.of(copy, catalog("Seed criteria")).link()).isEqualTo(CatalogLink.LOCALLY_EDITED);

        copy = practice("Seed criteria", fingerprintOf("Seed criteria"));

        assertThat(CatalogOrigin.of(copy, catalog("Seed criteria")).link()).isEqualTo(CatalogLink.IN_SYNC);
    }

    @Test
    void aCopyWhoseCatalogEntryIsNoLongerOfferedSaysSoWithoutChanging() {
        Practice copy = practice("Seed criteria", fingerprintOf("Seed criteria"));
        EffectiveCatalog retired = new EffectiveCatalog(
            List.of(),
            List.of(
                new CatalogEntry<>(
                    SLUG,
                    definition("Seed criteria"),
                    definition("Seed criteria"),
                    null,
                    null,
                    true,
                    0,
                    null
                )
            )
        );

        var origin = CatalogOrigin.of(copy, retired);
        assertThat(origin.link()).isEqualTo(CatalogLink.IN_SYNC);
        assertThat(origin.sourceOffered()).isFalse();
    }

    private static PracticeDefinition definition(String criteria) {
        return new PracticeDefinition(
            "Small PRs",
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            criteria,
            null,
            "Reason",
            null,
            null
        );
    }

    private static String fingerprintOf(String criteria) {
        return definition(criteria).detectionFingerprint(SLUG);
    }

    private static EffectiveCatalog catalog(String criteria) {
        return new EffectiveCatalog(List.of(), List.of(CatalogEntry.shippedOnly(SLUG, definition(criteria))));
    }

    private static Practice practice(String criteria, String copiedFromFingerprint) {
        Practice practice = new Practice();
        practice.setId(1L);
        practice.setSlug(SLUG);
        practice.setName("Small PRs");
        practice.setArtifactType(WorkArtifact.PULL_REQUEST);
        practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(List.of("PullRequestCreated")));
        practice.setCriteria(criteria);
        practice.setWhyItMatters("Reason");
        if (copiedFromFingerprint != null) {
            practice.setSourceCuratedSlug(SLUG);
            practice.setSourceCuratedFingerprint(copiedFromFingerprint);
        }
        practice.setCurrentRevision(new PracticeRevision(practice, 1));
        return practice;
    }
}

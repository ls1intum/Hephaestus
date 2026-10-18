package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PublicReviewEligibilityTest {
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final ItemRef ITEM = new ItemRef(1, 2, new ItemKind("scm.pull_request"), 3L, null, null);

    private final ReviewSnapshotQuery reviews = mock(ReviewSnapshotQuery.class);
    private final ItemDescriptorRegistry catalog = mock(ItemDescriptorRegistry.class);
    private final ItemDescriptor descriptor = mock(ItemDescriptor.class);
    private final FeedbackModeQuery modes = mock(FeedbackModeQuery.class);
    private final PublicReviewEligibility eligibility = new PublicReviewEligibility(reviews, catalog, modes);

    @Test
    void shouldPublishOnlyPinnedAuthorResultsWithProviderCopySupport() {
        review(pinned(10, 4, AboutRole.AUTHOR), pinned(11, 5, AboutRole.REVIEWER));
        when(modes.effectiveModes(anyLong(), any()))
                .thenReturn(Map.of(10L, FeedbackMode.APPROVE_FIRST, 11L, FeedbackMode.APPROVE_FIRST));
        var author = admitted(10, 4, 2, 6L);
        var reviewer = admitted(11, 5, 2, 7L);
        var unpinned = admitted(10, 99, 2, 6L);
        var wrongItem = admitted(10, 4, 99, 6L);
        var nobody = admitted(10, 4, 2, null);
        assertThat(eligibility.publicResultIds(job(), List.of(author, reviewer, unpinned, wrongItem, nobody)))
                .containsExactly(author.id());
        when(descriptor.copyCapabilities()).thenReturn(Set.of());
        assertThat(eligibility.publicResultIds(job(), List.of(author, reviewer)))
                .isEmpty();
    }

    // OFF stops public feedback only; the result itself stays admitted for private destinations.
    @Test
    void shouldKeepResultsOfAnOffPracticeOutOfPublicFeedback() {
        review(
                pinned(10, 4, AboutRole.AUTHOR),
                pinned(11, 5, AboutRole.AUTHOR),
                pinned(12, 6, AboutRole.AUTHOR),
                pinned(13, 7, AboutRole.AUTHOR));
        var approveFirst = admitted(10, 4, 2, 6L);
        var automatic = admitted(11, 5, 2, 6L);
        var off = admitted(12, 6, 2, 6L);
        var unresolved = admitted(13, 7, 2, 6L);
        when(modes.effectiveModes(1, List.of(10L, 11L, 12L, 13L)))
                .thenReturn(
                        Map.of(10L, FeedbackMode.APPROVE_FIRST, 11L, FeedbackMode.AUTOMATIC, 12L, FeedbackMode.OFF));

        assertThat(eligibility.publicResultIds(job(), List.of(approveFirst, automatic, off, unresolved)))
                .containsExactlyInAnyOrder(approveFirst.id(), automatic.id());
    }

    private void review(PinnedPractice... practices) {
        when(reviews.find(1, REVIEW_ID))
                .thenReturn(Optional.of(new ReviewSnapshot(
                        REVIEW_ID,
                        1,
                        ITEM,
                        ReviewKind.ITEM,
                        new ItemVersion("v", null),
                        ReviewOrigin.EVENT,
                        0,
                        List.of(practices))));
        when(catalog.descriptorFor(ITEM.kind())).thenReturn(Optional.of(descriptor));
        when(descriptor.copyCapabilities()).thenReturn(Set.of(CopyCapability.SUMMARY));
    }

    private static PinnedPractice pinned(long practiceId, long versionId, AboutRole about) {
        var definition = mock(PracticeDefinition.class);
        when(definition.about()).thenReturn(about);
        return new PinnedPractice(
                practiceId, versionId, definition, "practice-" + practiceId, mock(ReviewMethod.class));
    }

    private static ReviewJob job() {
        var job = TestEntities.reviewJob();
        job.setReviewId(REVIEW_ID);
        job.setWorkspaceId(1L);
        return job;
    }

    private static AdmittedResultSnapshot admitted(
            long practiceId, long versionId, long itemId, @Nullable Long developerId) {
        var candidate = mock(ResultCandidate.class);
        when(candidate.practiceId()).thenReturn(practiceId);
        when(candidate.practiceVersionId()).thenReturn(versionId);
        when(candidate.itemId()).thenReturn(itemId);
        when(candidate.developerId()).thenReturn(developerId);
        return new AdmittedResultSnapshot(UUID.randomUUID(), candidate, List.of());
    }
}

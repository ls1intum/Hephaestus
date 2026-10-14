package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PublicReviewEligibilityTest {
    @Test
    void shouldPublishOnlyPinnedAuthorResultsWithProviderCopySupport() {
        var reviews = mock(ReviewService.class);
        var catalog = mock(ItemCatalog.class);
        var descriptor = mock(ItemDescriptor.class);
        var job = mock(ReviewJob.class);
        UUID reviewId = UUID.randomUUID();
        when(job.getReviewId()).thenReturn(reviewId);
        when(job.getWorkspaceId()).thenReturn(1L);
        var item = new ItemRef(1, 2, new ItemKind("scm.pull_request"), 3L, null, null);
        var authorDefinition = mock(PracticeDefinition.class);
        var reviewerDefinition = mock(PracticeDefinition.class);
        when(authorDefinition.about()).thenReturn(AboutRole.AUTHOR);
        when(reviewerDefinition.about()).thenReturn(AboutRole.REVIEWER);
        when(reviews.find(1, reviewId))
                .thenReturn(Optional.of(new ReviewSnapshot(
                        reviewId,
                        1,
                        item,
                        ReviewKind.ITEM,
                        new ItemVersion("v", null),
                        ReviewOrigin.EVENT,
                        0,
                        List.of(
                                new PinnedPractice(10, 4, authorDefinition, "author", mock(ReviewMethod.class)),
                                new PinnedPractice(11, 5, reviewerDefinition, "reviewer", mock(ReviewMethod.class))))));
        when(catalog.descriptorFor(item.kind())).thenReturn(Optional.of(descriptor));
        when(descriptor.copyCapabilities()).thenReturn(Set.of(CopyCapability.SUMMARY));
        var author = admitted(10, 4, 2, 6L);
        var reviewer = admitted(11, 5, 2, 7L);
        var unpinned = admitted(10, 99, 2, 6L);
        var wrongItem = admitted(10, 4, 99, 6L);
        var nobody = admitted(10, 4, 2, null);
        var eligibility = new PublicReviewEligibility(reviews, catalog);
        assertThat(eligibility.publicResultIds(job, List.of(author, reviewer, unpinned, wrongItem, nobody)))
                .containsExactly(author.id());
        when(descriptor.copyCapabilities()).thenReturn(Set.of());
        assertThat(eligibility.publicResultIds(job, List.of(author, reviewer))).isEmpty();
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

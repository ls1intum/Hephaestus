package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredFeedbackPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredWorkFeedbackCopyDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredWorkFeedbackItemDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveredWorkFeedbackServiceTest extends BaseUnitTest {
    final ProductItemQuery items = mock(ProductItemQuery.class);
    final ProductReferenceQuery references = mock(ProductReferenceQuery.class);
    final DeliveredWorkFeedbackRepository ledger = mock(DeliveredWorkFeedbackRepository.class);
    final CurrentDeveloperLookup developer = mock(CurrentDeveloperLookup.class);
    final ResultRepository results = mock(ResultRepository.class);
    final ResultVisibilityPolicy visibility = mock(ResultVisibilityPolicy.class);
    final DeliveredWorkFeedbackService service =
            new DeliveredWorkFeedbackService(items, references, ledger, developer, results, visibility);

    @Test
    void missingItemReturnsNullItemAndEmptyMetadata() {
        when(items.atUrl(1, "https://example.test/item")).thenReturn(Optional.empty());
        var dto = service.get(1, "https://example.test/item");
        assertThat(dto.item()).isNull();
        assertThat(dto.feedback()).isEmpty();
        verifyNoInteractions(ledger);
    }

    @Test
    void missingCurrentAccountReturnsItemWithoutSomeoneElsesFeedback() {
        var ref = new ItemRef(1, 7, new ItemKind("scm.pull_request"), 7L, null, null);
        when(items.atUrl(1, "https://example.test/item")).thenReturn(Optional.of(ref));
        when(references.items(1, List.of(7L))).thenReturn(Map.of(7L, item()));
        when(developer.currentDeveloperId()).thenReturn(Optional.empty());
        assertThat(service.get(1, "https://example.test/item").feedback()).isEmpty();
        verifyNoInteractions(ledger);
    }

    @Test
    void inaccessibleEvidenceOmitsPracticeMetadataButKeepsConfirmedCopy() {
        var ref = new ItemRef(1, 7, new ItemKind("scm.pull_request"), 7L, null, null);
        var id = UUID.randomUUID();
        var result = UUID.randomUUID();
        var copy = new DeliveredWorkFeedbackCopyDTO(
                UUID.randomUUID(),
                FeedbackCopyKind.SUMMARY,
                "copy",
                "https://example.test/comment",
                null,
                null,
                null,
                null,
                Instant.EPOCH);
        when(items.atUrl(1, "https://example.test/item")).thenReturn(Optional.of(ref));
        when(references.items(1, List.of(7L))).thenReturn(Map.of(7L, item()));
        when(developer.currentDeveloperId()).thenReturn(Optional.of(3L));
        when(ledger.findDelivered(1, 3, 7))
                .thenReturn(List.of(new DeliveredWorkFeedbackItemDTO(
                        id,
                        Instant.EPOCH,
                        List.of(new DeliveredFeedbackPracticeDTO("practice", "Practice")),
                        List.of(copy))));
        when(ledger.basis(1, id)).thenReturn(List.of(result));
        when(results.findByWorkspaceIdAndIdIn(1, List.of(result))).thenReturn(List.of());
        when(visibility.permitsHistory(eq(1L), any(), any())).thenReturn(Set.of());
        var dto = service.get(1, "https://example.test/item");
        assertThat(dto.feedback()).singleElement().satisfies(f -> {
            assertThat(f.practices()).isEmpty();
            assertThat(f.copies()).containsExactly(copy);
        });
    }

    ItemRefDTO item() {
        return new ItemRefDTO("7", "scm.pull_request", null, null, "Item", null, null, null, null, null);
    }
}

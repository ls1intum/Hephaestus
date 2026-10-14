package de.tum.cit.aet.hephaestus.integration.outline.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentEvents;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OutlineDocumentEventRecorderTest extends BaseUnitTest {
    private final ItemWriter items = mock(ItemWriter.class);
    private final OutlineDocumentEventRecorder recorder = new OutlineDocumentEventRecorder(items);
    private static final Instant AT = Instant.parse("2026-08-07T09:00:00Z");

    @Test
    void shouldReturnTheDurableEventIdentityWhenTheProviderPublishesADocument() {
        UUID id = UUID.randomUUID();
        when(items.upsert(any())).thenReturn(new ItemRef(5L, 72L, DocumentEvents.DOCUMENT, 11L, null, null));
        when(items.record(any())).thenReturn(id);
        assertThat(recorder.record(5L, snapshot("body-hash"), "documents.publish", AT, FoundBy.WEBHOOK))
                .contains(id);
    }

    @Test
    void shouldRecordNothingWhenTheMirrorHasNoStableContent() {
        assertThat(recorder.record(5L, snapshot(null), "documents.publish", AT, FoundBy.WEBHOOK))
                .isEmpty();
        verifyNoInteractions(items);
    }

    @Test
    void shouldRecordNothingWhenTheProviderEventDoesNotChangeReviewedContent() {
        assertThat(recorder.record(5L, snapshot("body-hash"), "documents.move", AT, FoundBy.WEBHOOK))
                .isEmpty();
        verifyNoInteractions(items);
    }

    private static OutlineDocumentSnapshot snapshot(@Nullable String contentHash) {
        return new OutlineDocumentSnapshot(
                11L,
                "outline-id",
                "collection-id",
                "engineering",
                null,
                "Architecture",
                "architecture",
                null,
                AT,
                contentHash,
                null,
                contentHash == null ? null : 128,
                1L,
                null);
    }
}

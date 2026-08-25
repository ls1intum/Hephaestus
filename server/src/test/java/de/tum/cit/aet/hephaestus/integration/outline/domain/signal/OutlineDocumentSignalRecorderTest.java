package de.tum.cit.aet.hephaestus.integration.outline.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Recording is unconditional and triggering is policy — so what this class must get right is which
 * occurrences reach the ledger at all, and under which identity.
 */
class OutlineDocumentSignalRecorderTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 5L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-07T09:00:00Z");

    @Mock
    private SignalRecorder ledger;

    private OutlineDocumentSignalRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new OutlineDocumentSignalRecorder(ledger);
    }

    @Test
    void recordsAPublishAgainstTheMirrorRowAndItsContent() {
        when(ledger.record(any(), eq(OCCURRED_AT), eq(DiscoveredVia.EVENT))).thenReturn(true);

        Optional<SignalKey> recorded = recorder.record(
            WORKSPACE_ID,
            snapshot(11L, "hash-a", null),
            "documents.publish",
            OCCURRED_AT,
            DiscoveredVia.EVENT
        );

        ArgumentCaptor<SignalKey> key = ArgumentCaptor.forClass(SignalKey.class);
        verify(ledger).record(key.capture(), eq(OCCURRED_AT), eq(DiscoveredVia.EVENT));
        assertThat(key.getValue().workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(key.getValue().artifactId()).isEqualTo(11L);
        assertThat(key.getValue().signalName()).isEqualTo(DocsSignals.DOCUMENT_PUBLISHED);
        // The key is handed back so the caller can offer this very occurrence for review and settle the
        // row it just wrote; a boolean would have left it to reconstruct an identity it does not own.
        assertThat(recorded).contains(key.getValue());
    }

    @Test
    @DisplayName("a redelivery of the same revision is inert: the ledger, not this class, decides")
    void reportsWhatTheLedgerDecided() {
        when(ledger.record(any(), any(), any())).thenReturn(false);

        assertThat(
            recorder.record(
                WORKSPACE_ID,
                snapshot(11L, "hash-a", null),
                "documents.publish",
                OCCURRED_AT,
                DiscoveredVia.EVENT
            )
        ).isEmpty();
    }

    @Test
    @DisplayName("an event with no review meaning never reaches the ledger")
    void ignoresEventsThatChangeNothingADocumentSays() {
        assertThat(
            recorder.record(
                WORKSPACE_ID,
                snapshot(11L, "hash-a", null),
                "documents.move",
                OCCURRED_AT,
                DiscoveredVia.EVENT
            )
        ).isEmpty();

        verifyNoInteractions(ledger);
    }

    @Test
    @DisplayName("a tombstoned or absent subject is not an occurrence")
    void ignoresASubjectThatIsNotThere() {
        assertThat(
            recorder.record(WORKSPACE_ID, null, "documents.publish", OCCURRED_AT, DiscoveredVia.EVENT)
        ).isEmpty();
        assertThat(
            recorder.record(
                WORKSPACE_ID,
                snapshot(11L, "hash-a", Instant.parse("2026-08-06T00:00:00Z")),
                "documents.publish",
                OCCURRED_AT,
                DiscoveredVia.EVENT
            )
        ).isEmpty();

        verifyNoInteractions(ledger);
    }

    @Test
    @DisplayName("an evicted body has no revision, so nothing is recorded rather than something wrong")
    void skipsAContentSignalItCannotKey() {
        assertThat(
            recorder.record(
                WORKSPACE_ID,
                snapshot(11L, null, null),
                "documents.publish",
                OCCURRED_AT,
                DiscoveredVia.EVENT
            )
        ).isEmpty();

        verify(ledger, never()).record(any(), any(), any());
    }

    private static OutlineDocumentSnapshot snapshot(
        Long id,
        @Nullable String contentHash,
        @Nullable Instant deletedAt
    ) {
        return new OutlineDocumentSnapshot(
            id,
            "outline-uuid",
            "collection-uuid",
            "engineering",
            null,
            "Architecture decision",
            "architecture-decision",
            null,
            Instant.parse("2026-08-05T00:00:00Z"),
            contentHash,
            deletedAt,
            contentHash == null ? null : 128,
            1L
        );
    }
}

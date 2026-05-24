package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.outline.refs.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.refs.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener.ScopeDelta;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@DisplayName("OutlineLifecycleListener — unit")
class OutlineLifecycleListenerTest extends BaseUnitTest {

    private static final Instant FIXED = Instant.parse("2026-05-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Mock private ConnectionRepository connectionRepository;
    @Mock private OutlineCollectionRepository collectionRepository;
    @Mock private OutlineDocumentRepository documentRepository;

    private OutlineLifecycleListener listener;

    @BeforeEach
    void setUp() {
        listener = new OutlineLifecycleListener(connectionRepository, collectionRepository, documentRepository, CLOCK);
    }

    @Test
    @DisplayName("kind() returns OUTLINE")
    void kindIsOutline() {
        assertThat(listener.kind()).isEqualTo(IntegrationKind.OUTLINE);
    }

    @Test
    @DisplayName("empty delta is a no-op")
    void emptyDeltaIsNoOp() {
        listener.onScopeChanged(
            new IntegrationRef(IntegrationKind.OUTLINE, 1L, "ws"),
            new ScopeDelta(List.of(), List.of()));
        verify(connectionRepository, never()).findByWorkspaceIdAndKindAndInstanceKey(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("unknown connection logs + skips without exception")
    void unknownConnectionIsSilent() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.OUTLINE, 1L, "ws-gone");
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.OUTLINE, "ws-gone"))
            .thenReturn(Optional.empty());

        listener.onScopeChanged(ref, new ScopeDelta(List.of(), List.of("col_1")));

        verify(documentRepository, never()).softDeleteByCollection(anyLong(), anyLong(), anyString(), any());
        verify(collectionRepository, never())
            .softDeleteByConnectionIdAndCollectionIdIn(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("collection removal soft-deletes documents first, then the collection row (workspace-pinned)")
    void removalCascadesDocumentsThenCollection() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.OUTLINE, 1L, "ws");
        Connection conn = Mockito.mock(Connection.class);
        when(conn.getId()).thenReturn(77L);
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.OUTLINE, "ws"))
            .thenReturn(Optional.of(conn));
        when(documentRepository.softDeleteByCollection(1L, 77L, "col_1", FIXED)).thenReturn(5);
        when(collectionRepository.softDeleteByConnectionIdAndCollectionIdIn(1L, 77L, List.of("col_1"), FIXED))
            .thenReturn(1);

        listener.onScopeChanged(ref, new ScopeDelta(List.of(), List.of("col_1")));

        verify(documentRepository).softDeleteByCollection(1L, 77L, "col_1", FIXED);
        verify(collectionRepository).softDeleteByConnectionIdAndCollectionIdIn(1L, 77L, List.of("col_1"), FIXED);
    }
}

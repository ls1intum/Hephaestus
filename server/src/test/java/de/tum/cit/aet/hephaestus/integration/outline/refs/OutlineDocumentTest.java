package de.tum.cit.aet.hephaestus.integration.outline.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("OutlineDocument")
class OutlineDocumentTest extends BaseUnitTest {

    @Test
    @DisplayName("Constructor wires connection + documentId")
    void constructor() {
        Connection conn = Mockito.mock(Connection.class);
        OutlineDocument doc = new OutlineDocument(conn, "doc_42");
        assertThat(doc.getConnection()).isSameAs(conn);
        assertThat(doc.getDocumentId()).isEqualTo("doc_42");
        assertThat(doc.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("softDelete is idempotent — replay does not shift the tombstone")
    void softDeleteIdempotent() {
        OutlineDocument doc = new OutlineDocument(Mockito.mock(Connection.class), "doc_42");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        doc.softDelete(first);
        doc.softDelete(second);
        assertThat(doc.getDeletedAt()).isEqualTo(first);
        assertThat(doc.isDeleted()).isTrue();
    }
}

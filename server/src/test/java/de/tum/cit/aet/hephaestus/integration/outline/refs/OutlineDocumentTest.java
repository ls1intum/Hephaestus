package de.tum.cit.aet.hephaestus.integration.outline.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutlineDocumentTest extends BaseUnitTest {

    @Test
    void softDeleteIdempotentReplayPreservesOriginalTombstone() {
        OutlineDocument doc = new OutlineDocument(Mockito.mock(Connection.class), "doc_42");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        doc.softDelete(first);
        doc.softDelete(second);
        assertThat(doc.getDeletedAt()).isEqualTo(first);
        assertThat(doc.isDeleted()).isTrue();
    }
}

package de.tum.cit.aet.hephaestus.integration.outline.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
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
    @DisplayName("softDelete is idempotent — first call sets timestamp, replay does not shift it")
    void softDeleteIdempotent() {
        OutlineDocument doc = new OutlineDocument(Mockito.mock(Connection.class), "doc_42");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        doc.softDelete(first);
        doc.softDelete(second);
        assertThat(doc.getDeletedAt()).isEqualTo(first);
        assertThat(doc.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Setters cover revision-pointer fields")
    void setters() {
        OutlineDocument doc = new OutlineDocument(Mockito.mock(Connection.class), "doc_42");
        Instant modified = Instant.parse("2026-02-03T04:05:06Z");
        doc.setTitle("Spec");
        doc.setUrl("https://outline/doc_42");
        doc.setLastRevisionId("rev_99");
        doc.setLastModifiedAt(modified);
        doc.setCollectionId("col_1");
        assertThat(doc.getTitle()).isEqualTo("Spec");
        assertThat(doc.getUrl()).isEqualTo("https://outline/doc_42");
        assertThat(doc.getLastRevisionId()).isEqualTo("rev_99");
        assertThat(doc.getLastModifiedAt()).isEqualTo(modified);
        assertThat(doc.getCollectionId()).isEqualTo("col_1");
    }

    @Test
    @DisplayName("JPA mapping shape — outline_document table + uq_outline_document constraint")
    void jpaMappingShape() {
        assertThat(OutlineDocument.class.isAnnotationPresent(Entity.class)).isTrue();
        Table table = OutlineDocument.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("outline_document");
        UniqueConstraint[] uniques = table.uniqueConstraints();
        assertThat(uniques).hasSize(1);
        assertThat(uniques[0].name()).isEqualTo("uq_outline_document");
        assertThat(Arrays.asList(uniques[0].columnNames()))
            .containsExactly("connection_id", "document_id");
    }
}

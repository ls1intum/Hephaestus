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

@DisplayName("OutlineCollection")
class OutlineCollectionTest extends BaseUnitTest {

    @Test
    @DisplayName("Constructor wires connection + collection id")
    void constructor() {
        Connection conn = Mockito.mock(Connection.class);
        OutlineCollection col = new OutlineCollection(conn, "col_1", "Engineering");
        assertThat(col.getConnection()).isSameAs(conn);
        assertThat(col.getCollectionId()).isEqualTo("col_1");
        assertThat(col.getName()).isEqualTo("Engineering");
        assertThat(col.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete is idempotent")
    void softDeleteIdempotent() {
        OutlineCollection col = new OutlineCollection(Mockito.mock(Connection.class), "col_1", null);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        col.softDelete(first);
        col.softDelete(first.plusSeconds(86_400));
        assertThat(col.getDeletedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("JPA mapping shape — outline_collection table + uq_outline_collection constraint")
    void jpaMappingShape() {
        assertThat(OutlineCollection.class.isAnnotationPresent(Entity.class)).isTrue();
        Table table = OutlineCollection.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("outline_collection");
        UniqueConstraint[] uniques = table.uniqueConstraints();
        assertThat(uniques).hasSize(1);
        assertThat(uniques[0].name()).isEqualTo("uq_outline_collection");
        assertThat(Arrays.asList(uniques[0].columnNames()))
            .containsExactly("connection_id", "collection_id");
    }
}

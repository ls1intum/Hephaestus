package de.tum.cit.aet.hephaestus.integration.outline.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
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
}

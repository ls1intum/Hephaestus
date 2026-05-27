package de.tum.cit.aet.hephaestus.integration.outline.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutlineCollectionTest extends BaseUnitTest {

    @Test
    void softDeleteIdempotent() {
        OutlineCollection col = new OutlineCollection(Mockito.mock(Connection.class), "col_1", null);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        col.softDelete(first);
        col.softDelete(first.plusSeconds(86_400));
        assertThat(col.getDeletedAt()).isEqualTo(first);
    }
}

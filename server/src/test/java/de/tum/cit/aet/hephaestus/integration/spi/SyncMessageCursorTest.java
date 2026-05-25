package de.tum.cit.aet.hephaestus.integration.spi;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import de.tum.cit.aet.hephaestus.integration.spi.SyncMessage.Cursor;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SyncMessage.Cursor sealed variants")
class SyncMessageCursorTest extends BaseUnitTest {

    @Test
    void opaqueCursorWithoutExpiry() {
        Cursor.Opaque c = new Cursor.Opaque("github-page-token-abc");
        assertThat(c.value()).isEqualTo("github-page-token-abc");
        assertThat(c.validUntil()).isNull();
    }

    @Test
    void opaqueCursorWithExpiry() {
        Instant exp = Instant.now().plusSeconds(3600);
        Cursor.Opaque c = new Cursor.Opaque("slack-cursor", exp);
        assertThat(c.validUntil()).isEqualTo(exp);
    }

    @Test
    void numberedCursorHoldsTwoFields() {
        Cursor.Numbered c = new Cursor.Numbered(1000L, 950L);
        assertThat(c.highWatermark()).isEqualTo(1000L);
        assertThat(c.checkpoint()).isEqualTo(950L);
    }

    @Test
    void watermarkCursorHoldsSlackTs() {
        Cursor.Watermark c = new Cursor.Watermark("1700000000.123456");
        assertThat(c.stamp()).isEqualTo("1700000000.123456");
    }

    @Test
    void cursorSerializesAndDeserializesAcrossVariants() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

        Cursor[] originals = {
            new Cursor.Opaque("token-1"),
            new Cursor.Opaque("token-2", Instant.parse("2026-06-01T00:00:00Z")),
            new Cursor.Numbered(42L, 40L),
            new Cursor.Watermark("1700000000.999999")
        };
        for (Cursor original : originals) {
            String json = mapper.writeValueAsString(original);
            Cursor decoded = mapper.readValue(json, Cursor.class);
            assertThat(decoded).as("round-trip for %s", original).isEqualTo(original);
        }
    }
}

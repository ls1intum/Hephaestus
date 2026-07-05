package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The deriver builds {@code outline.<sub>.<event>} subjects (event lowercased, dots → {@code ~}) and a
 * stable, precise dedup key from subscription + event + document id, falling back to a body hash when
 * that triple is incomplete.
 */
class OutlineSubjectKeyDeriverTest extends BaseUnitTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OutlineSubjectKeyDeriver deriver = new OutlineSubjectKeyDeriver(mapper);

    private JsonNode json(String body) {
        return mapper.readTree(body);
    }

    @Test
    void kind_isOutline() {
        assertThat(deriver.kind()).isEqualTo(IntegrationKind.OUTLINE);
    }

    @Test
    void deriveSubject_sanitizesEventDots() {
        JsonNode payload = json("{\"webhookSubscriptionId\":\"sub-1\",\"event\":\"documents.update\"}");
        assertThat(deriver.deriveSubject(payload, Map.of())).isEqualTo("outline.sub-1.documents~update");
    }

    @Test
    void deriveSubject_fallsBackToUnknownTokens() {
        assertThat(deriver.deriveSubject(json("{}"), Map.of())).isEqualTo("outline.unknown.unknown");
    }

    @Test
    void deriveDedupKey_isStableForTheSameDocumentEvent() {
        byte[] body =
            "{\"webhookSubscriptionId\":\"sub-1\",\"event\":\"documents.update\",\"payload\":{\"id\":\"doc-9\"}}".getBytes(
                StandardCharsets.UTF_8
            );
        String first = deriver.deriveDedupKey(body, Map.of());
        String second = deriver.deriveDedupKey(body, Map.of());
        assertThat(first).isEqualTo(second).startsWith("outline-");
    }

    @Test
    void deriveDedupKey_differsAcrossDocumentsAndEvents() {
        byte[] update =
            "{\"webhookSubscriptionId\":\"s\",\"event\":\"documents.update\",\"payload\":{\"id\":\"a\"}}".getBytes(
                StandardCharsets.UTF_8
            );
        byte[] delete =
            "{\"webhookSubscriptionId\":\"s\",\"event\":\"documents.delete\",\"payload\":{\"id\":\"a\"}}".getBytes(
                StandardCharsets.UTF_8
            );
        byte[] otherDoc =
            "{\"webhookSubscriptionId\":\"s\",\"event\":\"documents.update\",\"payload\":{\"id\":\"b\"}}".getBytes(
                StandardCharsets.UTF_8
            );
        assertThat(deriver.deriveDedupKey(update, Map.of())).isNotEqualTo(deriver.deriveDedupKey(delete, Map.of()));
        assertThat(deriver.deriveDedupKey(update, Map.of())).isNotEqualTo(deriver.deriveDedupKey(otherDoc, Map.of()));
    }

    @Test
    void deriveDedupKey_fallsBackToBodyHashWhenTripleIncomplete() {
        byte[] body = "{\"webhookSubscriptionId\":\"s\",\"event\":\"documents.update\"}".getBytes(
            StandardCharsets.UTF_8
        );
        assertThat(deriver.deriveDedupKey(body, Map.of())).startsWith("outline-").hasSize("outline-".length() + 32);
    }
}

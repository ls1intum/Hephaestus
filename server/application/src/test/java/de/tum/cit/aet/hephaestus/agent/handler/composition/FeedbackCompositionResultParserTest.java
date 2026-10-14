package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
class FeedbackCompositionResultParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FeedbackCompositionResultParser parser = new FeedbackCompositionResultParser();
    private static final String ID = "00000000-0000-4000-8000-000000000001";

    private static String piece() {
        return "{\"destination\":\"ON_WORK\",\"resultIds\":[\""
                + ID
                + "\"],\"summary\":\"Add a focused test.\",\"notes\":[],\"withheldReason\":null}";
    }

    @Test
    void shouldParseFinalResultAndProblemReferences() {
        var parsed = parser.parse(mapper.readTree("[" + piece() + "]"));
        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().resultIds()).containsExactly(UUID.fromString(ID));
        assertThat(parsed.getFirst().summary()).isEqualTo("Add a focused test.");
    }

    @Test
    void shouldRejectCallerSuppliedCoordinatesAndUnknownKeys() {
        assertThatThrownBy(() -> parser.parse(mapper.readTree("["
                        + piece().replace(
                                        "\"notes\":[]",
                                        "\"notes\":[{\"problemId\":\""
                                                + ID
                                                + "\",\"body\":\"Test"
                                                + " it.\",\"path\":\"src/A.java\"}]")
                        + "]")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        parser.parse(mapper.readTree("[" + piece().replace("\"destination\"", "\"channel\"") + "]")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireNullableFieldsAndCanonicalIds() {
        assertThatThrownBy(() ->
                        parser.parse(mapper.readTree("[" + piece().replace(",\"withheldReason\":null", "") + "]")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(mapper.readTree("[" + piece().replace(ID, "1-1-1-1-1") + "]")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

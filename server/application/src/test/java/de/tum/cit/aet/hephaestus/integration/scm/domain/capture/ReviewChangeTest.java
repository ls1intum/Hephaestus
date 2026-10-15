package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ReviewChangeTest extends BaseUnitTest {
    private final JsonMapper mapper = new JsonMapper();

    @Test
    void shouldReadBothRenamePathsAndRemovedDiffTextFromCapture() {
        var captured = mapper.writeValueAsBytes(
                Map.of("changed_paths", List.of("before.ts", "after.ts"), "diff", "-@Test\n+changed();\n"));
        var change = ReviewChange.of(Map.of("context/change.json", captured), mapper);
        assertThat(change).isNotNull();
        assertThat(change.changedPaths()).containsExactlyInAnyOrder("before.ts", "after.ts");
        assertThat(change.text()).isEqualTo("-@Test\n+changed();\n");
    }

    @Test
    void shouldLeaveAnUncapturedChangeUndecidable() {
        assertThat(ReviewChange.of(Map.of(), mapper)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{not json",
                "{}",
                "{\"changed_paths\":[],\"diff\":null}",
                "{\"changed_paths\":[42],\"diff\":\"text\"}",
                "{\"changed_paths\":\"file.ts\",\"diff\":\"text\"}"
            })
    void shouldLeaveMalformedOrIncompleteChangeFactsUndecidable(String captured) {
        assertThat(ReviewChange.of(Map.of("context/change.json", captured.getBytes(StandardCharsets.UTF_8)), mapper))
                .isNull();
    }
}

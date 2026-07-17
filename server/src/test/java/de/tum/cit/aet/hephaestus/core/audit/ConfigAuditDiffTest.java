package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code changed_keys} is what per-control history (#1357) filters on, so its shape is an API
 * contract, not an implementation detail. Each test below pins one property that a plausible wrong
 * implementation would break.
 */
@Tag("unit")
class ConfigAuditDiffTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void reportsOnlyTheKeysThatDiffer() {
        // Fails if the diff returns every key rather than the changed ones — which would make the
        // per-control filter match every row and render the History panel useless.
        assertThat(changedKeys("{\"a\":1,\"b\":2}", "{\"a\":1,\"b\":3}")).containsExactly("b");
    }

    @Test
    void identicalSnapshotsYieldNoKeys() {
        // Drives no-op suppression: an idempotent PATCH must leave no row.
        assertThat(changedKeys("{\"a\":1}", "{\"a\":1}")).isEmpty();
    }

    @Test
    void clearingAnOverrideBackToInheritIsAChange() {
        // A cleared override is a real change, not an absent key. (That the mapper actually emits the
        // null is ConfigAuditIntegrationTest's job; this pins only that the diff treats it as a change.)
        assertThat(changedKeys("{\"cooldownMinutes\":30}", "{\"cooldownMinutes\":null}")).containsExactly(
            "cooldownMinutes"
        );
    }

    @Test
    void nestedChangeYieldsTheLeafPathNotTheContainer() {
        // Fails on a top-level-only implementation, which would report "volumeCaps" and leave
        // changedKey=volumeCaps.perPullRequest matching nothing — the column's whole purpose.
        assertThat(
            changedKeys(
                "{\"volumeCaps\":{\"perPullRequest\":5,\"perDay\":9}}",
                "{\"volumeCaps\":{\"perPullRequest\":3,\"perDay\":9}}"
            )
        ).containsExactly("volumeCaps.perPullRequest");
    }

    @Test
    void arraysCompareWholeRatherThanByIndex() {
        // Index paths would make the filter's value space depend on list order, which is not stable.
        assertThat(changedKeys("{\"slugs\":[\"a\",\"b\"]}", "{\"slugs\":[\"a\",\"c\"]}")).containsExactly("slugs");
    }

    @Test
    void aKeyAppearingOnOneSideOnlyIsAChange() {
        // Snapshot shapes evolve; a field that appeared or vanished is exactly what a reader needs.
        assertThat(changedKeys("{\"a\":1}", "{\"a\":1,\"b\":2}")).containsExactly("b");
    }

    @Test
    void createdListsEveryLeafOfTheNewState() {
        // Fails if create returns empty, which the recorder would then suppress as a no-op — losing
        // the creation event entirely.
        assertThat(ConfigAuditDiff.changedKeys(null, node("{\"a\":1,\"b\":{\"c\":2}}"))).containsExactly("a", "b.c");
    }

    @Test
    void deletedListsEveryLeafOfTheOldState() {
        assertThat(ConfigAuditDiff.changedKeys(node("{\"a\":1}"), null)).containsExactly("a");
    }

    @Test
    void keysAreSortedSoTheStoredArrayIsStable() {
        assertThat(changedKeys("{\"b\":1,\"a\":1}", "{\"b\":2,\"a\":2}")).containsExactly("a", "b");
    }

    private static List<String> changedKeys(String before, String after) {
        return ConfigAuditDiff.changedKeys(node(before), node(after));
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}

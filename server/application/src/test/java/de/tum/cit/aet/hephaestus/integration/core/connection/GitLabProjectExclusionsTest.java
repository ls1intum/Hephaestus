package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitLabProjectExclusionsTest extends BaseUnitTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void shouldReadAStopRecordedBeforeReceiptsAsExcludedWithoutAReceipt() {
        var legacy = new ConnectionConfig.GitLabProjectExclusions(Set.of(7001L), Set.of("Team/Legacy"));

        assertThat(legacy.excludes(7001L, "team/renamed")).isTrue();
        assertThat(legacy.excludes(null, "team/legacy")).isTrue();
        assertThat(legacy.stopReceipt(7001L)).isEmpty();
        assertThat(legacy.resolvePath(7002L, "team/legacy").stopReceipt(7002L)).isEmpty();
    }

    @Test
    void shouldReplaceTheReceiptWhenTheProjectIsStoppedAgainAndClearItOnInclude() {
        var other = ConnectionConfig.GitLabProjectExclusions.empty()
                .stop(7002L, "team/other", SECOND)
                .exclude(null, "team/legacy");

        var first = other.stop(7001L, "team/stopped", FIRST);
        var included = first.include(7001L, "team/stopped");
        var again = included.stop(7001L, "team/stopped", SECOND);

        assertThat(first.stopReceipt(7001L)).contains(FIRST);
        assertThat(included.excludes(7001L, "team/stopped")).isFalse();
        assertThat(included.stopReceipt(7001L)).isEmpty();
        assertThat(again.stopReceipt(7001L)).contains(SECOND);
        assertThat(first.stop(7001L, "team/stopped", SECOND).stopReceipt(7001L)).contains(SECOND);
        for (var exclusions : List.of(first, included, again)) {
            assertThat(exclusions.stopReceipt(7002L)).contains(SECOND);
            assertThat(exclusions.excludes(null, "team/legacy")).isTrue();
        }
    }

    @Test
    void shouldKeepTheProjectExcludedWhenItsReceiptIsSpent() {
        var stopped = ConnectionConfig.GitLabProjectExclusions.empty().stop(7001L, "team/stopped", FIRST);

        var spent = stopped.withoutStopReceipt(7001L);

        assertThat(spent.excludes(7001L, "team/stopped")).isTrue();
        assertThat(spent.stopReceipt(7001L)).isEmpty();
    }

    @Test
    void shouldRejectAReceiptForAProjectThatIsNotExcluded() {
        assertThatThrownBy(() -> new ConnectionConfig.GitLabProjectExclusions(Set.of(), Set.of(), Map.of(7001L, FIRST)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

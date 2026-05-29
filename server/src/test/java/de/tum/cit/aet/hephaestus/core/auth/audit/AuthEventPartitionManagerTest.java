package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure date-math unit tests for {@link AuthEventPartitionManager}: partition naming, the
 * create-ahead window, month parsing, and retention selection. No DB / Spring context.
 */
class AuthEventPartitionManagerTest extends BaseUnitTest {

    @Test
    void partitionName_encodesYearMonthAsAuthEventPYYYYMM() {
        assertThat(AuthEventPartitionManager.partitionName(YearMonth.of(2026, 5))).isEqualTo("auth_event_p202605");
        assertThat(AuthEventPartitionManager.partitionName(YearMonth.of(2026, 12))).isEqualTo("auth_event_p202612");
        assertThat(AuthEventPartitionManager.partitionName(YearMonth.of(2027, 1))).isEqualTo("auth_event_p202701");
    }

    @Test
    void partitionsToEnsure_coversCurrentPlusCreateAheadMonths_acrossYearBoundary() {
        List<YearMonth> months = AuthEventPartitionManager.partitionsToEnsure(YearMonth.of(2026, 11));

        // CREATE_AHEAD_MONTHS = 2 → current + next 2, rolling over the year boundary.
        assertThat(months).containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1));
    }

    @Test
    void monthFromPartitionName_parsesMonthlyPartitions() {
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event_p202605")).isEqualTo(YearMonth.of(2026, 5));
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event_p202701")).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    void monthFromPartitionName_returnsNullForDefaultAndMalformed() {
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event_default")).isNull();
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event")).isNull();
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event_p2026")).isNull(); // too short
        assertThat(AuthEventPartitionManager.monthFromPartitionName("auth_event_pYYYYMM")).isNull(); // not digits
        assertThat(AuthEventPartitionManager.monthFromPartitionName("some_other_table")).isNull();
        assertThat(AuthEventPartitionManager.monthFromPartitionName(null)).isNull();
    }

    @Test
    void partitionsToDrop_dropsStrictlyOlderThanRetentionWindow() {
        YearMonth current = YearMonth.of(2026, 5);
        // RETENTION_MONTHS = 12 → cutoff is 2025-05; partitions strictly before that are dropped.
        List<YearMonth> existing = List.of(
            YearMonth.of(2025, 3), // older than cutoff → drop
            YearMonth.of(2025, 4), // older than cutoff → drop
            YearMonth.of(2025, 5), // == cutoff → keep
            YearMonth.of(2025, 6), // newer → keep
            YearMonth.of(2026, 5) // current → keep
        );

        List<YearMonth> toDrop = AuthEventPartitionManager.partitionsToDrop(existing, current);

        assertThat(toDrop).containsExactly(YearMonth.of(2025, 3), YearMonth.of(2025, 4));
    }

    @Test
    void partitionsToDrop_keepsCutoffMonthAndEverythingNewer() {
        YearMonth current = YearMonth.of(2026, 1);
        // cutoff = 2025-01
        List<YearMonth> existing = List.of(YearMonth.of(2025, 1), YearMonth.of(2025, 2), YearMonth.of(2026, 1));

        assertThat(AuthEventPartitionManager.partitionsToDrop(existing, current)).isEmpty();
    }

    @Test
    void partitionsToDrop_handlesEmptyExistingList() {
        assertThat(AuthEventPartitionManager.partitionsToDrop(List.of(), YearMonth.of(2026, 5))).isEmpty();
    }
}

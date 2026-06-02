package de.tum.cit.aet.hephaestus.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

/**
 * The schedule resolver is the single source of truth shared by the scheduler, the Slack digest
 * task, and the league-points task — so its fallback + cron derivation must be exact.
 */
class LeaderboardScheduleResolverTest extends BaseUnitTest {

    private final LeaderboardProperties globalDefault = new LeaderboardProperties(
        new LeaderboardProperties.Schedule(1, "09:00"),
        new LeaderboardProperties.Notification(true)
    );
    private final LeaderboardScheduleResolver resolver = new LeaderboardScheduleResolver(globalDefault);

    private static Workspace workspace(Integer day, String time) {
        Workspace w = new Workspace();
        w.setId(1L);
        w.setLeaderboardScheduleDay(day);
        w.setLeaderboardScheduleTime(time);
        return w;
    }

    @Test
    void fallsBackToGlobalDefaultWhenUnset() {
        Workspace w = workspace(null, null);
        assertThat(resolver.day(w)).isEqualTo(1);
        assertThat(resolver.time(w)).isEqualTo("09:00");
        assertThat(resolver.cron(w)).isEqualTo("0 00 09 ? * 1");
    }

    @Test
    void usesWorkspaceOverride() {
        Workspace w = workspace(5, "17:30");
        assertThat(resolver.cron(w)).isEqualTo("0 30 17 ? * 5");
        assertThat(CronExpression.isValidExpression(resolver.cron(w))).isTrue();
    }

    @Test
    void defaultsMinuteToZeroWhenTimeHasNoMinuteComponent() {
        Workspace w = workspace(3, "8");
        assertThat(resolver.cron(w)).isEqualTo("0 0 8 ? * 3");
        assertThat(CronExpression.isValidExpression(resolver.cron(w))).isTrue();
    }

    @Test
    void cycleWindowIsExactlyOneWeekEndingAtTheScheduledMoment() {
        Workspace w = workspace(1, "09:00");
        LeaderboardScheduleResolver.CycleWindow window = resolver.previousCycleWindow(w);
        // before is the most recent Monday 09:00 at-or-before now; after is one week earlier.
        assertThat(window.after()).isEqualTo(window.before().minusSeconds(7 * 24 * 3600));
    }
}

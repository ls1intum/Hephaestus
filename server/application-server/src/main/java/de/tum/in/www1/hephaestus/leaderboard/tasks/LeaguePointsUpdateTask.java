package de.tum.in.www1.hephaestus.leaderboard.tasks;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeaguePointsUpdateTask implements Runnable {

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaderboardService leaderboardService;
    @Autowired
    private LeaguePointsCalculationService leaguePointsCalculationService;

    @Override
    @Transactional
    public void run() {
        List<LeaderboardEntryDTO> leaderboard = getLatestLeaderboard();
        leaderboard.forEach(updateLeaderboardEntry());
    }

    /**
     * Returns a consumer that updates the ranking points of a user based on its leaderboard entry.
     * @return
     */
    private Consumer<? super LeaderboardEntryDTO> updateLeaderboardEntry() {
        return entry -> {
            var user = userRepository.findByLoginWithEagerMergedPullRequests(entry.user().login()).orElseThrow();
            int newPoints = leaguePointsCalculationService.calculateNewPoints(user, entry);
            user.setLeaguePoints(newPoints);
            userRepository.save(user);
        };
    }

    /**
     * Returns the most recently completed leaderboard
     * @return
     */
    private List<LeaderboardEntryDTO> getLatestLeaderboard() {
        String[] timeParts = scheduledTime.split(":");
        OffsetDateTime before = OffsetDateTime.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(Integer.parseInt(scheduledDay))))
            .withHour(Integer.parseInt(timeParts[0]))
            .withMinute(timeParts.length > 0 ? Integer.parseInt(timeParts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        OffsetDateTime after = before.minusWeeks(1);
        return leaderboardService.createLeaderboard(after, before, Optional.empty());
    }
    
}

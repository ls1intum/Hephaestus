package de.tum.in.www1.hephaestus.teamleaderboard;

import de.tum.in.www1.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardSortType;
import org.jboss.resteasy.spi.NotImplementedYetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.threeten.bp.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TeamLeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderboardService.class);


    // TODO: private attributes that are needed for the class to fulfill it's duty

    // ---

    // TODO: add the method bodies to the according functions
    public List<TeamLeaderboardEntryDTO> createTeamLeaderboard(
        OffsetDateTime after,
        OffsetDateTime before,
        Optional<String> team,
        Optional<LeaderboardSortType> sort) {

        throw new NotImplementedYetException("[TeamLeaderboardService.createTeamLeaderboard] Missing function implementation to provide TeamLeaderboard data");
    }
}

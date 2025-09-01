package de.tum.in.www1.hephaestus.teamleaderboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardSortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TeamLeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderboardService.class);

    @Autowired
    private TeamRepository teamRepository;

    // TODO: private attributes that are needed for the class to fulfill it's duty

    // ---

    // TODO: add the method bodies to the according functions
    public List<TeamLeaderboardEntryDTO> createTeamLeaderboard(
        OffsetDateTime after,
        OffsetDateTime before,
        Optional<String> team,
        Optional<LeaderboardSortType> sort) {

        // Mock values for debug purpose

        RepositoryInfoDTO repoMock = new RepositoryInfoDTO(
            1L,
            "webapp",
            "team/webapp",
            "Frontend repo",
            "https://github.com/team/webapp"
        );

        LabelInfoDTO labelsMock = new LabelInfoDTO(
            1L,
            "bug",
            "#d73a4a",
            repoMock
        );

        UserInfoDTO membersMock = new UserInfoDTO(
            98L,
            "gandalf.the.white",
            "gandalf@ichhassesauron.au",
            "https://http.cat/305",
            "Gandalf der Weiße",
            "https://github.com/gandalf",
            19765
        );

        TeamInfoDTO teamInfoMock = new TeamInfoDTO(
            1L,
            "Frontend Masters",
            "#ffcc00",
            List.of(repoMock),
            List.of(labelsMock),
            List.of(membersMock),
            false
        );

        TeamLeaderboardEntryDTO entryMock = new TeamLeaderboardEntryDTO(
            1,
            1002,
            teamInfoMock,
            List.of(),
            100,
            12,
            85,
            1,
            6,
            42
        );

        return List.of(entryMock);
    }

}

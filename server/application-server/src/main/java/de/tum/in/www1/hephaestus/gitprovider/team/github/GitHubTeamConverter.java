package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import java.time.OffsetDateTime;
import org.kohsuke.github.GHTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubTeamConverter extends BaseGitServiceEntityConverter<GHTeam, Team> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamConverter.class);

    public Team convert(@NonNull GHTeam source) {
        return update(source, new Team());
    }

    @Override
    public Team update(@NonNull GHTeam source, @NonNull Team team) {
        convertBaseFields(source, team);
        team.setName(source.getName());
        team.setDescription(source.getDescription());
        team.setPrivacy(source.getPrivacy() == null ? null : Team.Privacy.valueOf(source.getPrivacy().name()));
        team.setHtmlUrl(source.getHtmlUrl().toString());
        team.setLastSyncedAt(OffsetDateTime.now());
        String organization = null;

        // getOrganization() may perform an API call and throw IOException.
        // If it fails, we log and leave the existing githubOrganization value intact.
        try {
            organization = source.getOrganization().getLogin();
        } catch (Exception e) {
            logger.error(
                "Error while fetching organization name for the following team: {}. {}",
                source.getName(),
                e.getMessage()
            );
        }

        if (organization != null) {
            team.setOrganization(organization);
        }

        return team;
    }
}

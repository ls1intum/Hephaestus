package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import java.time.OffsetDateTime;
import org.kohsuke.github.GHTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubTeamConverter extends BaseGitServiceEntityConverter<GHTeam, TeamV2> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamConverter.class);

    public TeamV2 convert(@NonNull GHTeam source) {
        return update(source, new TeamV2());
    }

    @Override
    public TeamV2 update(@NonNull GHTeam source, @NonNull TeamV2 team) {
        convertBaseFields(source, team);
        team.setName(source.getName());
        team.setDescription(source.getDescription());
        team.setPrivacy(source.getPrivacy() == null ? null : TeamV2.Privacy.valueOf(source.getPrivacy().name()));
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

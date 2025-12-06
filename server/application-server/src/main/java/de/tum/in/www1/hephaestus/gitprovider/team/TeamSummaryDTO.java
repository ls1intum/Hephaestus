package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.team.Team.Privacy;
import org.springframework.lang.NonNull;

/**
 * A lightweight DTO for team data that only contains basic information.
 * This is used in contexts where full team details (repositories, labels, members) are not needed,
 * avoiding expensive JOINs and lazy loading issues.
 */
public record TeamSummaryDTO(
    @NonNull Long id,
    @NonNull String name,
    Long parentId,
    String description,
    Privacy privacy,
    String organization,
    String htmlUrl,
    @NonNull Boolean hidden
) {
    public static TeamSummaryDTO fromTeam(Team team) {
        return new TeamSummaryDTO(
            team.getId(),
            team.getName(),
            team.getParentId(),
            team.getDescription(),
            team.getPrivacy(),
            team.getOrganization(),
            team.getHtmlUrl(),
            team.isHidden()
        );
    }
}

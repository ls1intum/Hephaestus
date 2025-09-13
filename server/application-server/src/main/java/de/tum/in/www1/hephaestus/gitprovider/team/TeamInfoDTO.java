package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.Team.Privacy;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

public record TeamInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    Long parentId,
    String description,
    Privacy privacy,
    String organization,
    String htmlUrl,
    @NonNull Boolean hidden,
    @NonNull List<RepositoryInfoDTO> repositories,
    @NonNull List<LabelInfoDTO> labels,
    @NonNull List<UserInfoDTO> members,
    @NonNull Integer membershipCount,
    @NonNull Integer repoPermissionCount
) {
    public static TeamInfoDTO fromTeam(Team team) {
        return new TeamInfoDTO(
            team.getId(),
            team.getName(),
            team.getParentId(),
            team.getDescription(),
            team.getPrivacy(),
            team.getOrganization(),
            team.getHtmlUrl(),
            team.isHidden(),
            team
                .getRepoPermissions()
                .stream()
                .map(rp -> RepositoryInfoDTO.fromRepository(rp.getRepository()))
                .distinct()
                .toList(),
            team.getLabels().stream().map(LabelInfoDTO::fromLabel).toList(),
            team
                .getMemberships()
                .stream()
                .map(m -> m.getUser())
                .filter(u -> u != null && !User.Type.BOT.equals(u.getType()))
                .map(UserInfoDTO::fromUser)
                .toList(),
            team.getMemberships().size(),
            team.getRepoPermissions().size()
        );
    }
}

package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

public record TeamInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String color,
    @NonNull List<RepositoryInfoDTO> repositories,
    @NonNull List<LabelInfoDTO> labels,
    @NonNull List<UserInfoDTO> members,
    @NonNull Boolean hidden
) {
    public static TeamInfoDTO fromTeam(Team team) {
        return new TeamInfoDTO(
            team.getId(),
            team.getName(),
            team.getColor(),
            team.getRepositories().stream().map(RepositoryInfoDTO::fromRepository).toList(),
            team.getLabels().stream().map(LabelInfoDTO::fromLabel).toList(),
            team
                .getMembers()
                .stream()
                .filter(user -> !user.getType().equals(User.Type.BOT))
                .map(UserInfoDTO::fromUser)
                .toList(),
            team.isHidden()
        );
    }
}

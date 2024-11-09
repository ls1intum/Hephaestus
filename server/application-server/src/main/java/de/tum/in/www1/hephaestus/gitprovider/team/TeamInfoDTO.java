package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

public record TeamInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String color,
    @NonNull List<RepositoryInfoDTO> repositories,
    @NonNull List<LabelInfoDTO> labels
) {
    public static TeamInfoDTO fromTeam(Team team) {
        return new TeamInfoDTO(
            team.getId(),
            team.getName(),
            team.getColor(),
            team.getRepositories().stream().map(RepositoryInfoDTO::fromRepository).toList(),
            team.getLabels().stream().map(LabelInfoDTO::fromLabel).toList()
        );
    }
}

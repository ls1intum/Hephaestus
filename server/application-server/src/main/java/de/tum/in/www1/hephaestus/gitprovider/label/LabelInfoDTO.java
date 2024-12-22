package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import org.springframework.lang.NonNull;

public record LabelInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String color,
    RepositoryInfoDTO repository
) {
    public static LabelInfoDTO fromLabel(Label label) {
        return new LabelInfoDTO(
            label.getId(),
            label.getName(),
            label.getColor(),
            RepositoryInfoDTO.fromRepository(label.getRepository())
        );
    }
}

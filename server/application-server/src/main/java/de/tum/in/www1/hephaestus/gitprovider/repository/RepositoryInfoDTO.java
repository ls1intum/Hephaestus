package de.tum.in.www1.hephaestus.gitprovider.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String nameWithOwner,
    String description,
    @NonNull String htmlUrl,
    List<LabelInfoDTO> labels
) {
    public static RepositoryInfoDTO fromRepository(Repository repository) {
        // Avoid circular references by setting the nested repository reference in LabelInfoDTO to null
        final List<LabelInfoDTO> labelDtos = repository
            .getLabels()
            .stream()
            .map(l -> new LabelInfoDTO(l.getId(), l.getName(), l.getColor(), null))
            .toList();

        return new RepositoryInfoDTO(
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getDescription(),
            repository.getHtmlUrl(),
            labelDtos
        );
    }
}

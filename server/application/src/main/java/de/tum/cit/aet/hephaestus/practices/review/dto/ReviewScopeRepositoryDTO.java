package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewScopeRepositoryDTO(
        @NotNull @Pattern(regexp = "[1-9][0-9]*") String repositoryItemId, List<String> baseBranches) {
    public ReviewScopeRepositoryDTO {
        baseBranches = List.copyOf(baseBranches);
    }
}

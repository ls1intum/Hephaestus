package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkspaceReviewScopeDTO(
        @NotNull ReviewScopeMode repositories,
        @NotNull ReviewScopeMode people,
        List<@Valid ReviewScopeRepositoryDTO> selectedRepositories,
        List<@Pattern(regexp = "[1-9][0-9]*") String> accountIds) {
    public WorkspaceReviewScopeDTO {
        selectedRepositories = List.copyOf(selectedRepositories);
        accountIds = List.copyOf(accountIds);
    }
}

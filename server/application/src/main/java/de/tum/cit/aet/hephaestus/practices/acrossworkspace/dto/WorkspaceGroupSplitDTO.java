package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.StandingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "One practice group: how the developers with a standing split across it, and where the reader is")
public record WorkspaceGroupSplitDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Group slug")
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Group name")
        String groupName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Group icon name") @Nullable
        String groupIcon,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Group colour name") @Nullable
        String groupColor,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The reader's current standing in the group, the one their practice profile shows:"
                        + " the part they are marked in. Absent unless the split counts the reader")
        @Nullable
        PracticeStanding yourStanding,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the reader's group verdict was read from, as their practice profile says. Absent"
                        + " without one")
        @Nullable
        StandingBasis yourBasis,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How the developers with a standing split across the group")
        WorkspaceSplitDTO split,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The group's practices review is admitted for, the same for every reader, each split"
                        + " on its own, in catalog order")
        List<WorkspacePracticeSplitDTO> practices) {
    public WorkspaceGroupSplitDTO {
        practices = List.copyOf(practices);
    }
}

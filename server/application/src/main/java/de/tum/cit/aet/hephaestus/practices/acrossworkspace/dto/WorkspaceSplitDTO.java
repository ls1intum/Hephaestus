package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        description = "How the developers with a standing split across one practice group or one practice, counted in"
                + " developers: a part per verdict, none yet, and their total")
public record WorkspaceSplitDTO(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "Developers at each verdict, Needs attention, Mixed feedback and Going well in that order")
        List<WorkspaceSplitPartDTO> parts,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Developers with a standing in a group shown but none here")
        int noneYet,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every developer the split counts, the parts and none yet together, the reader included"
                        + " when counted")
        int developers) {
    public WorkspaceSplitDTO {
        parts = List.copyOf(parts);
    }
}

package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStandingDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@PreAuthorize("@workspaceSecure.isMember()")
@RequestMapping("/practice-areas/standings")
@Tag(name = "Practice Area Standings", description = "The current developer's standing in each practice area")
@RequiredArgsConstructor
public class PracticeAreaStandingController {

    private final PracticeAreaService practiceAreaService;
    private final PracticeAreaStandingService practiceAreaStandingService;

    @GetMapping
    @Operation(
        operationId = "listPracticeAreaStandings",
        summary = "List the current developer's practice area standings",
        description = "Returns every active practice area's standing, direction, guidance, and supporting observations."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practice area standings returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaStandingDTO.class)))
    )
    public ResponseEntity<List<PracticeAreaStandingDTO>> listPracticeAreaStandings(WorkspaceContext context) {
        List<PracticeArea> areas = practiceAreaService.listAreas(context, true);
        return ResponseEntity.ok(practiceAreaStandingService.getAreaStandings(context.id(), areas));
    }
}

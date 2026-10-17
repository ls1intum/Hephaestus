package de.tum.cit.aet.hephaestus.practices.acrossworkspace;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceOverviewDTO;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceTileDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ConditionalOnServerRole
@WorkspaceScopedController
@RequestMapping("/practices")
@PreAuthorize("@workspaceSecure.isMember()")
@RequiredArgsConstructor
public class PracticesAcrossWorkspaceController {
    private final PracticesAcrossWorkspaceService query;

    @GetMapping("/workspace-overview")
    @Operation(operationId = "getPracticeWorkspaceOverview", summary = "Get practice results across the workspace")
    public PracticeWorkspaceOverviewDTO overview(WorkspaceContext ws) {
        return query.read(ws);
    }

    @GetMapping("/tiles")
    @Operation(operationId = "getPracticeWorkspaceTiles", summary = "List practice result totals across the workspace")
    public List<PracticeWorkspaceTileDTO> tiles(WorkspaceContext ws) {
        return query.read(ws).practices();
    }
}

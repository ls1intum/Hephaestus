package de.tum.in.www1.hephaestus.meta;

import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/meta")
@RequiredArgsConstructor
public class WorkspaceMetaController {

    private final MetaService metaService;

    @GetMapping
    public ResponseEntity<MetaDataDTO> getWorkspaceMeta(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(metaService.getWorkspaceMetaData(workspaceContext));
    }

    @GetMapping("/contributors")
    public ResponseEntity<List<ContributorDTO>> listWorkspaceContributors(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(metaService.getWorkspaceContributors(workspaceContext));
    }
}

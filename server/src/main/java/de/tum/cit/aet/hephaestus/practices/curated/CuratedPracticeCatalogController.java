package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeCatalogDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDetailDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/practice-catalog")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@RequiredArgsConstructor
public class CuratedPracticeCatalogController {

    private final CuratedPracticeService service;

    @GetMapping
    @Operation(summary = "Browse the curated practice catalog", operationId = "listCuratedPracticeCatalog")
    public ResponseEntity<CuratedPracticeCatalogDTO> list(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(
            new CuratedPracticeCatalogDTO(
                service.listAreas().stream().map(CuratedPracticeAreaDTO::from).toList(),
                service.list(false).stream().map(CuratedPracticeSummaryDTO::from).toList()
            )
        );
    }

    @GetMapping("/practices/{slug}")
    @Operation(summary = "Inspect an available curated practice", operationId = "getCuratedPracticeCatalogEntry")
    public ResponseEntity<CuratedPracticeDetailDTO> get(WorkspaceContext workspaceContext, @PathVariable String slug) {
        return ResponseEntity.ok(CuratedPracticeDetailDTO.from(service.get(slug, false)));
    }
}

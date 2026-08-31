package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/practice-catalog")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@Tag(name = "Practice Catalog", description = "Current instance definitions visible to workspace administrators")
@RequiredArgsConstructor
public class CuratedPracticeCatalogController {

    private final CuratedCatalogService service;

    @GetMapping("/practices/{slug}")
    @Operation(
            summary = "Read the instance's definition of a practice",
            description = "The current instance-catalog definition for comparison with a workspace copy.",
            operationId = "getCuratedPracticeCatalogEntry")
    public ResponseEntity<CuratedPracticeDTO> get(WorkspaceContext workspaceContext, @PathVariable String slug) {
        CatalogEntry<PracticeDefinition> entry = service.catalog().installablePractices().stream()
                .filter(candidate -> candidate.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Catalog practice", slug));
        return ResponseEntity.ok(CuratedPracticeDTO.from(entry));
    }
}

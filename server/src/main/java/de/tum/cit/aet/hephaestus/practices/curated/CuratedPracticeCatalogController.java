package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * What a workspace administrator can see of the instance catalog: the definition currently behind one
 * of their practices.
 *
 * <p>Read-only and single-entry on purpose. A workspace whose copy has drifted needs to see what the
 * instance offers now to judge its own; browsing entries it cannot adopt would only be a list of
 * things it cannot have, and adoption is separate work.
 */
@WorkspaceScopedController
@RequestMapping("/practice-catalog")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@RequiredArgsConstructor
public class CuratedPracticeCatalogController {

    private final CuratedCatalogService service;

    @GetMapping("/practices/{slug}")
    @Operation(
        summary = "Read the instance's definition of a practice",
        description = "The catalog definition a workspace copy came from, for comparison with the copy.",
        operationId = "getCuratedPracticeCatalogEntry"
    )
    public ResponseEntity<CuratedPracticeDTO> get(WorkspaceContext workspaceContext, @PathVariable String slug) {
        CatalogEntry<PracticeDefinition> entry = service
            .catalog()
            .practice(slug)
            .filter(CatalogEntry::offered)
            .orElseThrow(() -> new EntityNotFoundException("CuratedPractice", slug));
        return ResponseEntity.ok(CuratedPracticeDTO.from(entry));
    }
}

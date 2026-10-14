package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/practice-catalog")
@RequireAtLeastWorkspaceAdmin
@RequiredArgsConstructor
@ConditionalOnServerRole
public class WorkspaceCatalogController {
    private final CatalogService catalog;

    @GetMapping("/practices/{slug}")
    @Operation(operationId = "getPracticeCatalogEntry")
    public CatalogPracticeDTO get(WorkspaceContext ctx, @PathVariable String slug) {
        return catalog.practiceView(catalog.practice(slug));
    }
}

package de.tum.in.www1.hephaestus.meta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for global contributor information across all workspaces.
 * This endpoint is not workspace-scoped and returns aggregated data.
 */
@RestController
@RequestMapping("/contributors")
@RequiredArgsConstructor
@Tag(name = "Contributors", description = "Global contributor information")
public class ContributorController {

    private final MetaService metaService;

    /**
     * List all contributors across all workspaces.
     *
     * @return list of all contributors with their global contribution metrics
     */
    @GetMapping
    @Operation(summary = "List global contributors", description = "Returns all contributors across all workspaces")
    public ResponseEntity<List<ContributorDTO>> listGlobalContributors() {
        return ResponseEntity.ok(metaService.getGlobalContributors());
    }
}

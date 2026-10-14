package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeGroupRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeGroupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeGroupsRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.SetFeedbackModeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeGroupRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@WorkspaceScopedController
@RequestMapping("/practice-groups")
@RequiredArgsConstructor
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
public class PracticeGroupController {
    private final PracticeGroupService groups;
    private final PracticeReleaseService releases;
    private final CatalogOriginPresenter presenter;

    @GetMapping
    @Operation(operationId = "listPracticeGroups")
    public List<PracticeGroupDTO> list(
            WorkspaceContext ctx, @RequestParam(required = false) @Nullable Boolean shownToDevelopersOnly) {
        return presenter.presentGroups(ctx.id(), groups.listGroups(ctx, shownToDevelopersOnly));
    }

    @GetMapping("/{groupSlug}")
    @Operation(operationId = "getPracticeGroup")
    public ResponseEntity<PracticeGroupDTO> get(WorkspaceContext ctx, @PathVariable String groupSlug) {
        return ResponseEntity.ok()
                .eTag(releases.groupResetTag(ctx, groupSlug))
                .body(presenter.present(ctx.id(), groups.getGroup(ctx, groupSlug)));
    }

    @PostMapping
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_GROUP")
    @Operation(operationId = "createPracticeGroup")
    public ResponseEntity<PracticeGroupDTO> create(
            WorkspaceContext ctx, @RequestBody CreatePracticeGroupRequestDTO input) {
        var group = groups.createGroup(
                ctx,
                input.slug(),
                new GroupAttributes(input.name(), input.description(), input.position(), input.icon(), input.color()),
                input.shownToDevelopers());
        return ResponseEntity.created(
                        URI.create("/api/workspaces/" + ctx.slug() + "/practice-groups/" + group.getSlug()))
                .body(presenter.present(ctx.id(), group));
    }

    @PatchMapping("/{groupSlug}")
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_GROUP")
    @Operation(operationId = "updatePracticeGroup")
    public PracticeGroupDTO update(
            WorkspaceContext ctx, @PathVariable String groupSlug, @RequestBody UpdatePracticeGroupRequestDTO input) {
        return presenter.present(
                ctx.id(),
                groups.updateGroup(
                        ctx,
                        groupSlug,
                        new GroupAttributes(input.name(), input.description(), null, input.icon(), input.color()),
                        input.shownToDevelopers()));
    }

    @PatchMapping("/{groupSlug}/feedback-mode")
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_GROUP")
    @Operation(operationId = "setGroupFeedbackMode")
    public PracticeGroupDTO mode(
            WorkspaceContext ctx, @PathVariable String groupSlug, @RequestBody SetFeedbackModeRequestDTO input) {
        return presenter.present(ctx.id(), groups.setFeedbackMode(ctx, groupSlug, input.feedbackMode()));
    }

    @PatchMapping("/reorder")
    @AuditExempt(reason = "Order changes presentation only")
    @Operation(operationId = "reorderPracticeGroups")
    public List<PracticeGroupDTO> reorder(WorkspaceContext ctx, @RequestBody ReorderPracticeGroupsRequestDTO input) {
        groups.reorder(ctx, input.slugs());
        return presenter.presentGroups(ctx.id(), groups.listGroups(ctx, null));
    }

    @DeleteMapping("/{groupSlug}")
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_GROUP")
    @Operation(operationId = "deletePracticeGroup")
    public ResponseEntity<Void> delete(WorkspaceContext ctx, @PathVariable String groupSlug) {
        groups.deleteGroup(ctx, groupSlug);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupSlug}/catalog-customization")
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_GROUP")
    @Operation(operationId = "resetGroupToCatalog")
    public PracticeGroupDTO reset(
            WorkspaceContext ctx,
            @PathVariable String groupSlug,
            @RequestHeader(name = "If-Match", required = false) @Nullable String ifMatch) {
        return presenter.present(
                ctx.id(),
                releases.resetGroup(ctx, groupSlug, ifMatch == null ? null : EntityTagPrecondition.parse(ifMatch)));
    }
}

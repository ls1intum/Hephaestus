package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.validation.AccountId;
import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.security.UserViewContextHolder;
import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import de.tum.cit.aet.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.SetMemberHiddenRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@WorkspaceScopedController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService workspaceMembershipService;
    private final AccountReferenceService accounts;

    @GetMapping("/me")
    @Operation(operationId = "getOwnMembership", summary = "Get your workspace membership")
    public ResponseEntity<WorkspaceMembershipDTO> getCurrentUserMembership(WorkspaceContext context) {
        var view = UserViewContextHolder.get();
        Membership membership = workspaceMembershipService.getMembership(
                context.id(), view == null ? CurrentAccount.requireId() : view.accountId());
        WorkspaceRole effectiveRole =
                context.roles().stream().reduce(WorkspaceRole::highest).orElse(membership.getRole());
        if (effectiveRole != WorkspaceRole.OWNER
                && effectiveRole != WorkspaceRole.ADMIN
                && SecurityUtils.isSuperAdmin()) {
            effectiveRole = WorkspaceRole.ADMIN;
        }
        return ResponseEntity.ok(toDTO(context.id(), membership, effectiveRole));
    }

    @GetMapping
    @Operation(operationId = "listMemberships", summary = "List workspace memberships")
    public ResponseEntity<PageResponseDTO<WorkspaceMembershipDTO>> listMembers(
            WorkspaceContext context,
            @ParameterObject @PageableDefault(size = 50, sort = "joinedAt") Pageable pageable) {
        var page = workspaceMembershipService.listMembers(context.id(), pageable);
        var accountIds = page.getContent().stream()
                .map(member -> Objects.requireNonNull(member.getAccountId()))
                .toList();
        var references = accounts.references(context.id(), accountIds);
        var eligible = workspaceMembershipService.eligibleAccountIds(context.id(), accountIds);
        return ResponseEntity.ok(PageResponseDTO.from(page.map(member -> toDTO(
                member,
                member.getRole(),
                Objects.requireNonNull(references.get(member.getAccountId())),
                eligible.contains(member.getAccountId())))));
    }

    @GetMapping("/{accountId}")
    @Operation(operationId = "getMembership", summary = "Get a workspace membership")
    public ResponseEntity<WorkspaceMembershipDTO> getMember(
            WorkspaceContext context, @PathVariable @AccountId String accountId) {
        Membership membership = workspaceMembershipService.getMembership(context.id(), Long.parseLong(accountId));
        return ResponseEntity.ok(toDTO(context.id(), membership, membership.getRole()));
    }

    @PostMapping("/assign")
    @Operation(operationId = "assignMemberRole", summary = "Assign a workspace role")
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "WORKSPACE_ROLE")
    public ResponseEntity<WorkspaceMembershipDTO> assignRole(
            WorkspaceContext context, @Valid @RequestBody AssignRoleRequestDTO request) {
        Membership membership = workspaceMembershipService.assignRole(
                context.id(), Long.parseLong(request.accountId()), request.role());
        return ResponseEntity.ok(toDTO(context.id(), membership, membership.getRole()));
    }

    @PatchMapping("/{accountId}/hidden")
    @Operation(operationId = "setMemberHidden", summary = "Change a member’s activity visibility")
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "WORKSPACE_ROLE")
    public ResponseEntity<WorkspaceMembershipDTO> updateMemberVisibility(
            WorkspaceContext context,
            @PathVariable @AccountId String accountId,
            @Valid @RequestBody SetMemberHiddenRequestDTO request) {
        Membership membership = workspaceMembershipService.updateMemberVisibility(
                context.id(), Long.parseLong(accountId), request.hidden());
        return ResponseEntity.ok(toDTO(context.id(), membership, membership.getRole()));
    }

    @DeleteMapping("/{accountId}")
    @Operation(operationId = "removeMember", summary = "Remove a workspace member")
    @ApiResponse(responseCode = "204", description = "Membership removed")
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "WORKSPACE_ROLE")
    public ResponseEntity<Void> removeMember(WorkspaceContext context, @PathVariable @AccountId String accountId) {
        workspaceMembershipService.removeMembership(context.id(), Long.parseLong(accountId));
        return ResponseEntity.noContent().build();
    }

    private WorkspaceMembershipDTO toDTO(long workspaceId, Membership member, WorkspaceRole role) {
        long accountId = Objects.requireNonNull(member.getAccountId());
        return toDTO(
                member,
                role,
                accounts.reference(workspaceId, accountId),
                workspaceMembershipService.isPracticeReviewEligible(workspaceId, accountId));
    }

    private WorkspaceMembershipDTO toDTO(
            Membership member, WorkspaceRole role, AccountRefDTO account, boolean eligible) {
        return new WorkspaceMembershipDTO(
                account,
                role,
                member.getJoinedAt(),
                member.getLeftAt(),
                member.isHidden(),
                member.isReviewSelected(),
                eligible);
    }
}

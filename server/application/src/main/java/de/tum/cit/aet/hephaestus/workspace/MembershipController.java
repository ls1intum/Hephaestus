package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.security.UserViewContextHolder;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import de.tum.cit.aet.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.SetMemberHiddenRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@WorkspaceScopedController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService workspaceMembershipService;
    private final AccountReferenceService accounts;

    /** One representative membership, with the account's effective workspace role. */
    @GetMapping("/me")
    @Operation(operationId = "getOwnMembership", summary = "Get your workspace membership")
    public ResponseEntity<WorkspaceMembershipDTO> getCurrentUserMembership(WorkspaceContext context) {
        Membership membership = workspaceMembershipService.getMembership(
                context.id(),
                UserViewContextHolder.get() == null
                        ? CurrentAccount.requireId()
                        : Objects.requireNonNull(UserViewContextHolder.get()).accountId());
        WorkspaceRole effectiveRole = context.roles().stream()
                .reduce((first, next) -> first.isAtLeast(next) ? first : next)
                .orElse(membership.getRole());
        if (effectiveRole != WorkspaceRole.OWNER
                && effectiveRole != WorkspaceRole.ADMIN
                && SecurityUtils.isSuperAdmin()) {
            effectiveRole = WorkspaceRole.ADMIN;
        }
        return ResponseEntity.ok(toDTO(context.id(), membership, effectiveRole));
    }

    @GetMapping
    @Operation(operationId = "listMemberships", summary = "List workspace memberships")
    public ResponseEntity<List<WorkspaceMembershipDTO>> listMembers(
            WorkspaceContext context,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Results per page, capped at 100") @RequestParam(defaultValue = "50") @Min(1)
                    int size) {
        int pageSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("joinedAt").ascending());

        List<WorkspaceMembershipDTO> memberships = workspaceMembershipService
                .listMembers(context.id(), pageable)
                .map(member -> toDTO(context.id(), member, member.getRole()))
                .getContent();

        return ResponseEntity.ok(memberships);
    }

    @GetMapping("/{accountId}")
    @Operation(operationId = "getMembership", summary = "Get a workspace membership")
    public ResponseEntity<WorkspaceMembershipDTO> getMember(
            WorkspaceContext context,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") @DecimalMax("9223372036854775807") String accountId) {
        Membership membership = requireMembership(context.id(), Long.parseLong(accountId));
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
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") @DecimalMax("9223372036854775807") String accountId,
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
    public ResponseEntity<Void> removeMember(
            WorkspaceContext context,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") @DecimalMax("9223372036854775807") String accountId) {
        workspaceMembershipService.removeMembership(context.id(), Long.parseLong(accountId));
        return ResponseEntity.noContent().build();
    }

    private WorkspaceMembershipDTO toDTO(long workspaceId, Membership member, WorkspaceRole role) {
        return new WorkspaceMembershipDTO(
                Objects.requireNonNull(member.getAccountId()).toString(),
                accounts.reference(workspaceId, Objects.requireNonNull(member.getAccountId())),
                role,
                member.getJoinedAt(),
                member.getLeftAt(),
                member.isHidden(),
                member.isReviewSelected(),
                workspaceMembershipService.isPracticeReviewEligible(
                        workspaceId, Objects.requireNonNull(member.getAccountId())));
    }

    private Membership requireMembership(Long workspaceId, Long accountId) {
        return workspaceMembershipService
                .findMembership(workspaceId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", accountId));
    }
}

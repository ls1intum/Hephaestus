package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.core.auth.validation.AccountId;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

public record AssignRoleRequestDTO(
        @NonNull @NotNull @AccountId String accountId,

        @NonNull @NotNull WorkspaceRole role) {}

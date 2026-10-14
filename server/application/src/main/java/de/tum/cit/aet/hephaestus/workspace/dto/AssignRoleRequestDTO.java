package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.NonNull;

public record AssignRoleRequestDTO(
        @NonNull @NotNull @Pattern(regexp = "^[1-9][0-9]*$") @DecimalMax("9223372036854775807")
        String accountId,

        @NonNull @NotNull WorkspaceRole role) {}

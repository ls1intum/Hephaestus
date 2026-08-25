package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

@Schema(description = "Whether new workspaces receive this catalog entry")
public record UpdateCuratedStatusRequestDTO(@NonNull @NotNull(message = "Status is required") CuratedStatus status) {}

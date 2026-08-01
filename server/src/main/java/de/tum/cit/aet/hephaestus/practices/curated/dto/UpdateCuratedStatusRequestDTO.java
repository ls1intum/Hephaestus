package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Retire a catalog entry or offer it again. Used for both practices and areas. */
@Schema(description = "Whether the instance offers this catalog entry to workspaces")
public record UpdateCuratedStatusRequestDTO(@NotNull(message = "Status is required") CuratedStatus status) {}

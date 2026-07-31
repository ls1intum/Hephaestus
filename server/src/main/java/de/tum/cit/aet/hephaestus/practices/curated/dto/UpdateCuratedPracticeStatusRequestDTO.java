package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeStatus;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

public record UpdateCuratedPracticeStatusRequestDTO(@NonNull @NotNull CuratedPracticeStatus status) {}

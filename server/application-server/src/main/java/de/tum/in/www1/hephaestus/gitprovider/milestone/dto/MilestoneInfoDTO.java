package de.tum.in.www1.hephaestus.gitprovider.milestone.dto;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone.State;

public record MilestoneInfoDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull State state,
    @NonNull String title,
    String description,
    OffsetDateTime closedAt,
    OffsetDateTime dueOn,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
package de.tum.in.www1.hephaestus.gitprovider.milestone.dto;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone.State;

public record MilestoneInfoDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull State state,
    @NonNull String title,
    String description,
    String closedAt,
    String dueOn,
    @NonNull String htmlUrl,
    @NonNull String createdAt,
    @NonNull String updatedAt
) {
}
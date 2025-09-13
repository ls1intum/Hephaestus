package de.tum.in.www1.hephaestus.gitprovider.milestone;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone.State;
import java.time.Instant;
import org.springframework.lang.NonNull;

public record MilestoneInfoDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull State state,
    @NonNull String title,
    String description,
    Instant closedAt,
    Instant dueOn,
    @NonNull String htmlUrl,
    Instant createdAt,
    Instant updatedAt
) {}

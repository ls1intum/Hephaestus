package de.tum.in.www1.hephaestus.gitprovider.milestone;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone.State;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

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
) {}

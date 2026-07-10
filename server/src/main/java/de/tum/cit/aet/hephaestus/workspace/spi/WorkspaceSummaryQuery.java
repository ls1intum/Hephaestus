package de.tum.cit.aet.hephaestus.workspace.spi;

import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface WorkspaceSummaryQuery {
    Optional<WorkspaceSummary> findById(long workspaceId);

    record WorkspaceSummary(
        long id,
        @NonNull String slug,
        @NonNull String displayName,
        @Nullable Long mentorConfigId
    ) {}
}

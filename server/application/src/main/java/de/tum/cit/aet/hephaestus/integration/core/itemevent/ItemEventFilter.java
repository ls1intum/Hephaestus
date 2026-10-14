package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ItemEventFilter(
        long workspaceId,
        @Nullable Long itemId,
        @Nullable Instant from,
        @Nullable Instant to,
        boolean withoutReview) {}

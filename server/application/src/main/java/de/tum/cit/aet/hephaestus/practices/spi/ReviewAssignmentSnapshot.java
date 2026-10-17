package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import org.jspecify.annotations.Nullable;

public record ReviewAssignmentSnapshot(
        ReviewAssignmentId id,
        ItemRef item,
        ItemVersion version,
        @Nullable Long developerId,
        String practiceSlug,
        PinnedPractice practice) {}

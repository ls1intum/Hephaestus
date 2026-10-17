package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

public record ReviewAssignmentId(UUID reviewId, long practiceId, long itemId) {}

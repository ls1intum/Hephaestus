package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedbackDispatchInsert(
    UUID id,
    String destinationKey,
    Long workspaceId,
    UUID agentJobId,
    @Nullable UUID feedbackId,
    String destination,
    String body,
    @Nullable String targetExternalRef,
    String practiceSlugs
) {}

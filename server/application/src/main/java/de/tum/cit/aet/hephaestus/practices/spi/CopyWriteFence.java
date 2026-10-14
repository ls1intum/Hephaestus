package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;

public record CopyWriteFence(UUID feedbackId, UUID copyId, UUID chatMessageId, Instant writeStartedAt) {}

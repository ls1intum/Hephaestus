package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;

public record PreparedFeedback(UUID feedbackId, String packageDigest, Instant preparedAt) {}

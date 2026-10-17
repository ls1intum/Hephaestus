package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;

public record ResultMarkChanged(
        UUID markId, long workspaceId, UUID resultId, ResultMarkKind kind, boolean removed, Instant occurredAt) {}

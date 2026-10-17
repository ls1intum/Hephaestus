package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReviewTimingFactsQuery {
    ReviewTimingFacts facts(long workspaceId, List<UUID> reviewIds, Instant from, Instant to);

    List<ReviewTimingFact> timelines(long workspaceId, List<UUID> reviewIds);

    List<ReviewTimingFact> window(long workspaceId, Instant from, Instant to);
}

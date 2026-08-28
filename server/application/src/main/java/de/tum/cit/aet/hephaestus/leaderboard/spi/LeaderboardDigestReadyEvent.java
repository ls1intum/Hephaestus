package de.tum.cit.aet.hephaestus.leaderboard.spi;

import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Per-workspace digest emitted by the weekly task; vendor adapters subscribe to publish.
 * One event per workspace-channel target — subscribers MUST treat each event as a
 * self-contained unit of work so a failure on one workspace cannot block another.
 *
 * @param teamLabel               optional team filter; {@code null} → all teams
 * @param currentDateEpochSeconds publish-time epoch seconds embedded in the digest so all
 *                                events in one cron tick render the same wall-clock
 * @param topEntries              pre-ranked by {@code LeaderboardService}; never empty
 *                                (the publisher suppresses empty digests)
 */
public record LeaderboardDigestReadyEvent(
    long workspaceId,
    String workspaceSlug,
    String channelId,
    @Nullable String teamLabel,
    long currentDateEpochSeconds,
    Instant after,
    Instant before,
    List<LeaderboardEntryDTO> topEntries,
    String baseUrl
) {}

package de.tum.cit.aet.hephaestus.leaderboard.spi;

import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Vendor-neutral signal that a per-workspace leaderboard digest is ready to publish.
 *
 * <p>Emitted once per qualifying workspace-channel target by the leaderboard module's
 * weekly task. Vendor adapters (today: Slack under
 * {@code integration/slack/leaderboard/}) subscribe to fan out the digest onto their
 * channel — the leaderboard side owns schedule + data assembly; vendors own the publish.
 *
 * <p>Fan-out granularity is intentionally per-target: one workspace → one channel → one
 * event. A workspace failing to publish must not block another workspace, so the
 * publisher iterates connections and fires events independently. Subscribers MUST treat
 * each event as a self-contained unit of work.
 *
 * <p>{@code topEntries} comes pre-ranked from {@code LeaderboardService}; subscribers
 * should not re-rank. {@code channelId} is the destination-specific channel handle (Slack
 * channel ID today; for future Teams/Discord this is the equivalent target identifier
 * negotiated by the integration's own config — the leaderboard side just forwards what
 * the connection config exposes).
 *
 * @param workspaceId            scope of the digest (the workspace whose leaderboard this represents)
 * @param workspaceSlug          for building deep-links into the web UI
 * @param channelId              vendor-specific target identifier (e.g. Slack channel ID)
 * @param teamLabel              optional team filter applied to the leaderboard query
 *                               ({@code null} → all teams)
 * @param currentDateEpochSeconds publish-time epoch seconds, embedded into the digest for
 *                               "posted at" rendering (separated from {@code Instant.now()}
 *                               at subscriber time so all per-workspace events in a single
 *                               cron tick render the same wall-clock)
 * @param after                  inclusive window start (typically a week before {@code before})
 * @param before                 exclusive window end (typically the cron firing instant)
 * @param topEntries             ranked top-N leaderboard rows for the workspace; never null,
 *                               never empty (the leaderboard task suppresses empty events)
 * @param baseUrl                normalised hephaestus host base URL for deep-links (no
 *                               trailing slash, scheme-qualified)
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

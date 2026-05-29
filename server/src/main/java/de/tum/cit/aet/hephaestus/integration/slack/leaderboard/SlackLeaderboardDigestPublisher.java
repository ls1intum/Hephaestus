package de.tum.cit.aet.hephaestus.integration.slack.leaderboard;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.Blocks.divider;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.spi.LeaderboardDigestReadyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Slack-side half of the weekly leaderboard digest fan-out.
 *
 * <p>Subscribes to {@link LeaderboardDigestReadyEvent} (one event per workspace-channel
 * target, emitted by {@code SlackWeeklyLeaderboardTask}) and performs the vendor-specific
 * work the leaderboard module is no longer allowed to touch: resolving Slack users via
 * fuzzy name/email matching, building the {@code chat.postMessage} block kit payload,
 * and sending. The leaderboard task owns schedule + data assembly; this class owns
 * publish.
 *
 * <p>{@link EventListener} (not {@code TransactionalEventListener}) is correct here:
 * the publisher is a cron task with no surrounding transaction the publish needs to
 * coordinate with, and any subscriber that needed AFTER_COMMIT ordering would also
 * need a transaction on the publish side — there isn't one.
 *
 * <p>Failure modes preserved from the original task:
 * <ul>
 *   <li>{@link SlackSendException} on send → log + swallow, so one workspace's Slack outage
 *       does not abort the (synchronous) listener for the others. Each workspace is published
 *       under its own event.
 *   <li>No matching Slack users for top entries → log + skip publish (same outcome as the
 *       original "reason=noQualifiedReviewers" branch, but moved to the vendor side
 *       because user-resolution requires the Slack SDK).
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackLeaderboardDigestPublisher {

    private static final Logger log = LoggerFactory.getLogger(SlackLeaderboardDigestPublisher.class);
    private static final String FALLBACK_TEXT = "Weekly review highlights";

    private final SlackMessageService slackMessageService;

    public SlackLeaderboardDigestPublisher(SlackMessageService slackMessageService) {
        this.slackMessageService = slackMessageService;
    }

    @EventListener
    public void onDigestReady(LeaderboardDigestReadyEvent event) {
        List<User> allSlackUsers = slackMessageService.listMembers(event.workspaceId());
        List<User> topReviewers = event
            .topEntries()
            .stream()
            .map(mapToSlackUser(allSlackUsers))
            .filter(user -> user != null)
            .toList();
        if (topReviewers.isEmpty()) {
            log.info(
                "Skipped Slack notification: reason=noQualifiedReviewers, workspaceId={}, channelId={}",
                event.workspaceId(),
                event.channelId()
            );
            return;
        }

        List<LayoutBlock> blocks = buildBlocks(event, topReviewers);
        try {
            slackMessageService.sendForWorkspace(event.workspaceId(), event.channelId(), blocks, FALLBACK_TEXT);
        } catch (SlackSendException e) {
            log.warn(
                "Failed to send scheduled Slack message: workspaceId={}, channelId={}, slackError={}",
                event.workspaceId(),
                e.channelId(),
                e.slackError()
            );
        }
    }

    private Function<LeaderboardEntryDTO, User> mapToSlackUser(List<User> allSlackUsers) {
        return entry -> {
            UserInfoDTO leaderboardUser = entry.user();
            if (leaderboardUser == null) {
                return null;
            }

            var exactUser = allSlackUsers
                .stream()
                .filter(
                    user ->
                        user.getName().equalsIgnoreCase(leaderboardUser.name()) ||
                        (user.getProfile() != null &&
                            user.getProfile().getEmail() != null &&
                            user.getProfile().getEmail().equalsIgnoreCase(leaderboardUser.email()))
                )
                .findFirst();
            if (exactUser.isPresent()) {
                return exactUser.get();
            }

            // Fall back to nearest-name match, but only within a small edit budget so a
            // contributor with no Slack counterpart is dropped (filtered out upstream) rather
            // than @-mentioning an unrelated person in the public digest.
            return nearestNameMatch(leaderboardUser.name(), allSlackUsers);
        };
    }

    /**
     * Nearest Slack user by Levenshtein distance on real-name (falling back to handle), accepted
     * only if the edit distance is within a budget proportional to the target length (≤30%, min 1,
     * capped at 4). Returns {@code null} when the target is blank or no candidate is close enough —
     * a deliberately conservative match to avoid mentioning the wrong person. Null/blank candidate
     * names are skipped so a deactivated/bot account never trips the matcher.
     */
    static User nearestNameMatch(String targetName, List<User> candidates) {
        if (targetName == null || targetName.isBlank()) {
            return null;
        }
        LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();
        User best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (User candidate : candidates) {
            String candidateName = candidate.getRealName() != null ? candidate.getRealName() : candidate.getName();
            if (candidateName == null || candidateName.isBlank()) {
                continue;
            }
            int d = distance.apply(targetName, candidateName);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        int maxAllowed = Math.min(4, Math.max(1, (targetName.length() * 3) / 10));
        return bestDistance <= maxAllowed ? best : null;
    }

    private List<LayoutBlock> buildBlocks(LeaderboardDigestReadyEvent event, List<User> topReviewers) {
        final String baseUrl = event.baseUrl();
        final String workspaceBase = baseUrl + "/w/" + event.workspaceSlug();
        String teamFilter = event.teamLabel() == null ? "all" : event.teamLabel();
        return asBlocks(
            header(header -> header.text(plainText(pt -> pt.text(":newspaper: Weekly review highlights :newspaper:")))),
            context(context ->
                context.elements(
                    List.of(
                        markdownText(
                            "<!date^" +
                                event.currentDateEpochSeconds() +
                                "^{date} at {time}| Today at 9:00AM CEST> | " +
                                workspaceBase
                        )
                    )
                )
            ),
            divider(),
            section(section ->
                section.text(
                    markdownText(
                        "Last week's review leaderboard is finalized. See where you landed <" +
                            workspaceBase +
                            "?after=" +
                            encode(formatDateForURL(event.after())) +
                            "&before=" +
                            encode(formatDateForURL(event.before())) +
                            "&team=" +
                            encode(teamFilter) +
                            "|here>."
                    )
                )
            ),
            section(section -> section.text(markdownText("Congrats to last week's top 3 reviewers:"))),
            section(section ->
                section.text(
                    markdownText(
                        IntStream.range(0, topReviewers.size())
                            .mapToObj(i -> ((i + 1) + ". <@" + topReviewers.get(i).getId() + ">"))
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("")
                    )
                )
            ),
            section(section -> section.text(markdownText("Thanks for keeping reviews moving! :rocket:")))
        );
    }

    private String formatDateForURL(Instant instant) {
        // ISO-8601 for query params (e.g., 2025-01-01T00:00:00Z); the leaderboard URL expects this format.
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

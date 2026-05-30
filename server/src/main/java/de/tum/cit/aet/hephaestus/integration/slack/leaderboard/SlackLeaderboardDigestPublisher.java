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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * work the leaderboard module is no longer allowed to touch: resolving Slack users for an
 * {@code @}-mention, building the {@code chat.postMessage} block kit payload, and sending.
 * The leaderboard task owns schedule + data assembly; this class owns publish.
 *
 * <p><b>User resolution is exact-match only</b> — a top reviewer is {@code @}-mentioned only
 * when their leaderboard handle or email matches a Slack member's handle or email
 * case-insensitively. There is deliberately <b>no</b> fuzzy / edit-distance matching: a
 * near-miss must never {@code @}-mention an unrelated person in a public channel. A reviewer
 * with no Slack account is rendered as a <b>plain name</b> (not dropped), so the digest always
 * lists the real top&nbsp;3.
 *
 * <p>{@link EventListener} (not {@code TransactionalEventListener}) is correct here:
 * the publisher is a cron task with no surrounding transaction the publish needs to
 * coordinate with.
 *
 * <p>{@link SlackSendException} on send → log + swallow, so one workspace's Slack outage
 * does not abort the (synchronous) listener; each workspace is published under its own event.
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

        // Render every top entry: an exact-matched reviewer becomes a real <@id> mention, an
        // unmatched one becomes a plain name — never dropped, never fuzzy-mentioned.
        List<String> rankedMentions = event
            .topEntries()
            .stream()
            .map(entry -> mentionFor(entry, allSlackUsers))
            .filter(s -> s != null && !s.isBlank())
            .toList();
        if (rankedMentions.isEmpty()) {
            log.info(
                "Skipped Slack notification: reason=noReviewersToRender, workspaceId={}, channelId={}",
                event.workspaceId(),
                event.channelId()
            );
            return;
        }

        List<LayoutBlock> blocks = buildBlocks(event, rankedMentions);
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

    /**
     * Slack mention for a leaderboard entry: {@code <@id>} when the entry exactly matches a Slack
     * member by handle or email (case-insensitive), otherwise the reviewer's plain display name.
     * Returns {@code null} only for a team-aggregate row (no individual user).
     */
    static String mentionFor(LeaderboardEntryDTO entry, List<User> allSlackUsers) {
        UserInfoDTO reviewer = entry.user();
        if (reviewer == null) {
            return null;
        }
        return exactMatch(reviewer, allSlackUsers)
            .map(user -> "<@" + user.getId() + ">")
            .orElseGet(() -> plainName(reviewer));
    }

    /** Deterministic, case-insensitive match on Slack handle or profile email. No fuzzy fallback. */
    private static java.util.Optional<User> exactMatch(UserInfoDTO reviewer, List<User> allSlackUsers) {
        return allSlackUsers
            .stream()
            .filter(
                user ->
                    (reviewer.name() != null && reviewer.name().equalsIgnoreCase(user.getName())) ||
                    (reviewer.email() != null &&
                        user.getProfile() != null &&
                        reviewer.email().equalsIgnoreCase(user.getProfile().getEmail()))
            )
            .findFirst();
    }

    /** Best human-readable name for an unmatched reviewer: display name, else login. */
    private static String plainName(UserInfoDTO reviewer) {
        if (reviewer.name() != null && !reviewer.name().isBlank()) {
            return reviewer.name();
        }
        return reviewer.login();
    }

    private List<LayoutBlock> buildBlocks(LeaderboardDigestReadyEvent event, List<String> rankedMentions) {
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
                        IntStream.range(0, rankedMentions.size())
                            .mapToObj(i -> ((i + 1) + ". " + rankedMentions.get(i)))
                            .collect(Collectors.joining("\n"))
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

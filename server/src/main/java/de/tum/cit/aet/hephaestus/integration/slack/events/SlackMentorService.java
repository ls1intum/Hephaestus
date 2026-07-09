package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRequest;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRunner;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackStreamingMentorChannel;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Bridges an inbound Slack DM to the mentor: resolves the workspace (from the Slack team) and developer, finds or
 * creates the mentor {@code chat_thread} for that DM, then runs a turn through the {@link MentorTurnRunner} port,
 * streaming the reply natively via {@link SlackStreamingMentorChannel}.
 *
 * <p>The Slack→mentor thread mapping is persisted with the {@code integration.slack.domain} JPA repository; the
 * {@code chat_thread} itself is created inside the mentor module (no cross-module raw insert). The find-or-create
 * of that mapping is delegated to {@link MentorSlackThreadLinker} so its two writes commit atomically across a real
 * proxy hop. Developer identity resolves through {@code identity_link} (SLACK provider) via
 * {@link SlackMentorIdentityResolver}: an unlinked Slack user (or one with no membership in the resolved
 * workspace) gets the friendly "not linked" reply.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorService {

    private static final Logger log = LoggerFactory.getLogger(SlackMentorService.class);

    private final SlackWorkspaceResolver workspaceResolver;
    private final MentorSlackThreadLinker threadLinker;
    private final MentorTurnRunner mentorTurnRunner;
    private final SlackMessageService slackMessageService;
    private final SlackMentorIdentityResolver identityResolver;
    private final SlackMentorInputGuard inputGuard;
    private final SlackOnboardingService onboardingService;

    public SlackMentorService(
        SlackWorkspaceResolver workspaceResolver,
        MentorSlackThreadLinker threadLinker,
        MentorTurnRunner mentorTurnRunner,
        SlackMessageService slackMessageService,
        SlackMentorIdentityResolver identityResolver,
        SlackMentorInputGuard inputGuard,
        SlackOnboardingService onboardingService
    ) {
        this.workspaceResolver = workspaceResolver;
        this.threadLinker = threadLinker;
        this.mentorTurnRunner = mentorTurnRunner;
        this.slackMessageService = slackMessageService;
        this.identityResolver = identityResolver;
        this.inputGuard = inputGuard;
        this.onboardingService = onboardingService;
    }

    private record Developer(String login) {}

    /**
     * The developer whose practice history the DM should draw on, resolved via {@code identity_link} (SLACK
     * provider) keyed by {@code (team, user)} and gated on membership in {@code workspaceId}.
     */
    private Optional<Developer> resolveDeveloper(long workspaceId, String teamId, String slackUserId) {
        return identityResolver.resolveDeveloperLogin(workspaceId, teamId, slackUserId).map(Developer::new);
    }

    /**
     * Handle one inbound DM message: resolve → run a mentor turn that streams its reply back into
     * {@code threadTs}'s thread on {@code channelId}.
     */
    public void handleDm(
        String teamId,
        String channelId,
        String slackUserId,
        String text,
        String messageTs,
        String threadTs
    ) {
        if (text == null || text.isBlank()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            log.warn("Slack DM for unknown/inactive team {} — no workspace connection", teamId);
            return;
        }
        long workspaceId = workspaceOpt.get();
        SlackMentorInputGuard.Verdict verdict = inputGuard.decide(text);
        if (!verdict.allowsMentorTurn()) {
            if (verdict.responseText() != null && !verdict.responseText().isBlank()) {
                slackMessageService.sendForWorkspace(
                    workspaceId,
                    channelId,
                    threadTs,
                    List.of(),
                    verdict.responseText()
                );
            }
            log.info("Slack DM diverted by input guard: workspace={} action={}", workspaceId, verdict.action());
            return;
        }
        Optional<Developer> devOpt = resolveDeveloper(workspaceId, teamId, slackUserId);
        if (devOpt.isEmpty()) {
            slackMessageService.sendForWorkspace(
                workspaceId,
                channelId,
                threadTs,
                onboardingService.linkCtaBlocks(),
                "Connect your Slack account to Hephaestus so the mentor can find your work."
            );
            return;
        }
        Developer dev = devOpt.get();
        // Find-or-create the Slack↔mentor thread mapping in its own atomic transaction (a real proxy hop), before
        // any remote Slack streaming — handleDm itself stays outside any transaction.
        UUID threadId = threadLinker.findOrCreateThread(
            workspaceId,
            teamId,
            channelId,
            threadTs,
            slackUserId,
            dev.login()
        );
        // First feedback to the member on turn receipt, before any token streams (superseded by the first append).
        slackMessageService.setStatus(workspaceId, channelId, threadTs, "Reviewing recent feedback...");
        // Stream the reply into the active Slack DM thread (event.thread_ts when present, else the user's message ts).
        SlackStreamingMentorChannel channel = new SlackStreamingMentorChannel(
            slackMessageService,
            workspaceId,
            channelId,
            threadTs
        );
        mentorTurnRunner.run(
            MentorTurnRequest.slackDm(
                workspaceId,
                threadId,
                text,
                deterministicSlackMessageId(teamId, channelId, messageTs)
            ),
            channel,
            dev.login()
        );
        log.info("Accepted Slack mentor turn: workspace={} thread={} developer={}", workspaceId, threadId, dev.login());
    }

    private static UUID deterministicSlackMessageId(String teamId, String channelId, String messageTs) {
        String key = "slack:" + teamId + ":" + channelId + ":" + messageTs;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}

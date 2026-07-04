package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRequest;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRunner;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackStreamingMentorChannel;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
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
    private final SlackMentorQuotaGuard quotaGuard;
    private final SlackSafetyClassifier safetyClassifier;

    public SlackMentorService(
        SlackWorkspaceResolver workspaceResolver,
        MentorSlackThreadLinker threadLinker,
        MentorTurnRunner mentorTurnRunner,
        SlackMessageService slackMessageService,
        SlackMentorIdentityResolver identityResolver,
        SlackMentorQuotaGuard quotaGuard,
        SlackSafetyClassifier safetyClassifier
    ) {
        this.workspaceResolver = workspaceResolver;
        this.threadLinker = threadLinker;
        this.mentorTurnRunner = mentorTurnRunner;
        this.slackMessageService = slackMessageService;
        this.identityResolver = identityResolver;
        this.quotaGuard = quotaGuard;
        this.safetyClassifier = safetyClassifier;
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
     * {@code messageTs}'s thread on {@code channelId}.
     */
    public void handleDm(String teamId, String channelId, String slackUserId, String text, String messageTs) {
        if (text == null || text.isBlank()) {
            return;
        }
        Optional<Long> workspaceOpt = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceOpt.isEmpty()) {
            log.warn("Slack DM for unknown/inactive team {} — no workspace connection", teamId);
            return;
        }
        long workspaceId = workspaceOpt.get();
        // Message classification: divert obvious harassment / self-harm / out-of-scope messages to a canned
        // response before any mentor turn runs. The classifier is a seam; the shipped default is only an
        // obvious-abuse keyword fast-path (NOT crisis detection) — a non-OK verdict never mentors, but an OK
        // verdict only means "no cheap signal", not a safety guarantee.
        SlackSafetyClassifier.Verdict verdict = safetyClassifier.classify(text);
        if (!verdict.safeToMentor()) {
            slackMessageService.sendForWorkspace(workspaceId, channelId, List.of(), verdict.cannedResponse());
            log.info(
                "Slack DM diverted by safety classifier: workspace={} category={}",
                workspaceId,
                verdict.category()
            );
            return;
        }
        Optional<Developer> devOpt = resolveDeveloper(workspaceId, teamId, slackUserId);
        if (devOpt.isEmpty()) {
            slackMessageService.sendForWorkspace(
                workspaceId,
                channelId,
                List.of(),
                "Your Slack account isn't linked to a Hephaestus developer yet, so I can't pull up your practice history. Ask an admin to link you."
            );
            return;
        }
        Developer dev = devOpt.get();
        // Per-user turn/day + fleet daily-budget caps (this path is not covered by the HTTP-auth rate-limit
        // filter). Over-cap posts a friendly message rather than throwing.
        SlackMentorQuotaGuard.Decision quota = quotaGuard.tryAcquire(workspaceId + ":" + dev.login());
        if (quota != SlackMentorQuotaGuard.Decision.ALLOWED) {
            slackMessageService.sendForWorkspace(workspaceId, channelId, List.of(), overCapMessage(quota));
            log.info(
                "Slack mentor turn over cap: workspace={} developer={} decision={}",
                workspaceId,
                dev.login(),
                quota
            );
            return;
        }
        // Find-or-create the Slack↔mentor thread mapping in its own atomic transaction (a real proxy hop), before
        // any remote Slack streaming — handleDm itself stays outside any transaction.
        UUID threadId = threadLinker.findOrCreateThread(workspaceId, teamId, channelId, slackUserId, dev.login());
        // First feedback to the member on turn receipt, before any token streams (superseded by the first append).
        slackMessageService.setStatus(workspaceId, channelId, messageTs, "Reviewing your recent feedback…");
        // Stream the reply into the DM thread rooted at the user's message.
        SlackStreamingMentorChannel channel = new SlackStreamingMentorChannel(
            slackMessageService,
            workspaceId,
            channelId,
            messageTs
        );
        mentorTurnRunner.run(MentorTurnRequest.slackDm(workspaceId, threadId, text), channel, dev.login());
        log.info("Started Slack mentor turn: workspace={} thread={} developer={}", workspaceId, threadId, dev.login());
    }

    /** The friendly, non-throwing reply shown when a DM is over a mentor quota. */
    private static String overCapMessage(SlackMentorQuotaGuard.Decision decision) {
        return switch (decision) {
            case USER_CAP_EXCEEDED -> "You've reached your mentor limit for today — let's pick this back up tomorrow. " +
            "In the meantime, take a look at the feedback already on your recent PRs and issues.";
            case DAILY_BUDGET_EXCEEDED -> "The mentor is at capacity for today and can't take new questions right now. " +
            "Please try again tomorrow.";
            case ALLOWED -> ""; // unreachable — ALLOWED never routes here
        };
    }
}

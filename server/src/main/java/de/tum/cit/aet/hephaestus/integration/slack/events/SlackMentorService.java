package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorSlackThreadService;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRequest;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRunner;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges an inbound Slack DM to the mentor: resolves the workspace (from the Slack team) and developer, finds or
 * creates the mentor {@code chat_thread} for that DM, then runs a turn through the {@link MentorTurnRunner} port,
 * streaming the reply natively via {@link SlackStreamingMentorChannel}.
 *
 * <p>The Slack→mentor thread mapping is persisted with the {@code integration.slack.domain} JPA repository; the
 * {@code chat_thread} itself is created inside the mentor module via {@link MentorSlackThreadService} (no
 * cross-module raw insert). Developer identity resolves through {@code identity_link} (SLACK provider) via
 * {@link SlackMentorIdentityResolver}: an unlinked Slack user (or one with no membership in the resolved
 * workspace) gets the friendly "not linked" reply.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorService {

    private static final Logger log = LoggerFactory.getLogger(SlackMentorService.class);

    private final SlackWorkspaceResolver workspaceResolver;
    private final MentorSlackThreadRepository mentorSlackThreadRepository;
    private final MentorSlackThreadService mentorSlackThreadService;
    private final MentorTurnRunner mentorTurnRunner;
    private final SlackMessageService slackMessageService;
    private final SlackMentorIdentityResolver identityResolver;

    public SlackMentorService(
        SlackWorkspaceResolver workspaceResolver,
        MentorSlackThreadRepository mentorSlackThreadRepository,
        MentorSlackThreadService mentorSlackThreadService,
        MentorTurnRunner mentorTurnRunner,
        SlackMessageService slackMessageService,
        SlackMentorIdentityResolver identityResolver
    ) {
        this.workspaceResolver = workspaceResolver;
        this.mentorSlackThreadRepository = mentorSlackThreadRepository;
        this.mentorSlackThreadService = mentorSlackThreadService;
        this.mentorTurnRunner = mentorTurnRunner;
        this.slackMessageService = slackMessageService;
        this.identityResolver = identityResolver;
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
     * Find (or lazily create) the mentor {@code chat_thread} that backs this Slack DM. The mapping row lives in
     * {@code integration.slack.domain}; the {@code chat_thread} is provisioned inside the mentor module.
     */
    @Transactional
    UUID findOrCreateThread(
        long workspaceId,
        String teamId,
        String channelId,
        String slackUserId,
        String developerLogin
    ) {
        return mentorSlackThreadRepository
            .findByWorkspaceIdAndSlackChannelId(workspaceId, channelId)
            .map(MentorSlackThread::getChatThreadId)
            .orElseGet(() -> {
                UUID chatThreadId = mentorSlackThreadService.ensureSlackThread(workspaceId, null, developerLogin);
                MentorSlackThread mapping = new MentorSlackThread();
                mapping.setId(UUID.randomUUID());
                mapping.setWorkspaceId(workspaceId);
                mapping.setChatThreadId(chatThreadId);
                mapping.setSlackTeamId(teamId);
                mapping.setSlackChannelId(channelId);
                mapping.setSlackUserId(slackUserId);
                mentorSlackThreadRepository.save(mapping);
                return chatThreadId;
            });
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
        UUID threadId = findOrCreateThread(workspaceId, teamId, channelId, slackUserId, dev.login());
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
}

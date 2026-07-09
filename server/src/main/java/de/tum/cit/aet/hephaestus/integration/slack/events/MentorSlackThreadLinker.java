package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorSlackThreadService;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic boundary for linking a Slack DM to its mentor {@code chat_thread}.
 *
 * <p>This is a <b>separate bean</b> from {@link SlackMentorService} on purpose: the find-or-create writes two
 * rows across two modules — the {@code chat_thread} is provisioned inside the mentor module via
 * {@link MentorSlackThreadService#ensureSlackThread} and the {@code integration.slack.domain} mapping row is
 * saved here — and both must commit or roll back together. {@code @Transactional} only takes effect across a real
 * proxy hop, so if {@link SlackMentorService#handleDm} self-invoked a {@code @Transactional} method on itself the
 * annotation would be ignored and a failure between the two writes would orphan a {@code chat_thread} row. Hoisting
 * the pair into a public {@code @Transactional} method on its own bean makes the atomicity real. {@code handleDm}
 * is deliberately <em>not</em> transactional (it makes remote Slack streaming calls that must stay outside any tx),
 * so this uses plain {@code @Transactional} (no {@code REQUIRES_NEW}) — there is no ambient caller transaction to
 * join, so a new one is started for the two writes.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class MentorSlackThreadLinker {

    private final MentorSlackThreadRepository mentorSlackThreadRepository;
    private final MentorSlackThreadService mentorSlackThreadService;

    public MentorSlackThreadLinker(
        MentorSlackThreadRepository mentorSlackThreadRepository,
        MentorSlackThreadService mentorSlackThreadService
    ) {
        this.mentorSlackThreadRepository = mentorSlackThreadRepository;
        this.mentorSlackThreadService = mentorSlackThreadService;
    }

    /**
     * Find (or lazily create) the mentor {@code chat_thread} that backs this Slack DM. The mapping row lives in
     * {@code integration.slack.domain}; the {@code chat_thread} is provisioned inside the mentor module. Both
     * writes commit atomically within this method's transaction.
     */
    @Transactional
    public UUID findOrCreateThread(
        long workspaceId,
        String teamId,
        String channelId,
        String threadTs,
        String slackUserId,
        String developerLogin
    ) {
        return mentorSlackThreadRepository
            .findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(workspaceId, channelId, threadTs)
            .map(MentorSlackThread::getChatThreadId)
            .orElseGet(() -> {
                UUID chatThreadId = mentorSlackThreadService.ensureSlackThread(workspaceId, null, developerLogin);
                MentorSlackThread mapping = new MentorSlackThread();
                mapping.setId(UUID.randomUUID());
                mapping.setWorkspaceId(workspaceId);
                mapping.setChatThreadId(chatThreadId);
                mapping.setSlackTeamId(teamId);
                mapping.setSlackChannelId(channelId);
                mapping.setSlackThreadTs(threadTs);
                mapping.setSlackUserId(slackUserId);
                mentorSlackThreadRepository.save(mapping);
                return chatThreadId;
            });
    }
}

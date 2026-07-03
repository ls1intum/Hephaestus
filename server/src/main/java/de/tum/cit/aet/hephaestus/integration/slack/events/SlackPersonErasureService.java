package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases one person's already-stored Slack data within one workspace — the GDPR Art. 17 half of a person opt-out
 * (and the future account hard-delete). Symmetric to {@link SlackIngestService#eraseChannel}, but person-scoped
 * rather than channel-scoped. Transactional, idempotent, and strictly tenant + person scoped: it never touches
 * another user's rows, another workspace's rows, or the PR/ISSUE surface.
 *
 * <p>Three deletes, all keyed by the resolved workspace member id:
 * <ol>
 *   <li>the person's raw {@code slack_message} rows (keyed by the {@code author_member_id} firewall stamp);</li>
 *   <li>the person's id pruned out of every thread's {@code participant_member_ids} array
 *       ({@link SlackThreadRepository#pruneParticipant} — {@code array_remove}, leaving co-participants intact);</li>
 *   <li>the derived {@code CONVERSATION_THREAD} observations/feedback the person is the subject of
 *       ({@link ConversationFeedbackErasure#eraseConversationFeedbackAboutUser}, {@code about_user_id}).</li>
 * </ol>
 *
 * <p><strong>Account hard-delete.</strong> This is the exact method an account hard-delete would call per
 * workspace the account is a member of. It is intentionally NOT wired into {@code AccountPurger} yet: that path is
 * account-global (raw-JDBC child deletes, no per-workspace member resolution and no purge-contributor SPI), so
 * resolving an account's {@code (workspaceId, memberId, slackUserId)} tuples there requires a new cross-module SPI —
 * a separate slice. Until then, whole-workspace teardown (workspace purge / app uninstall, via
 * {@code SlackWorkspacePurgeAdapter}) remains the account-delete backstop for Slack-derived data.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackPersonErasureService {

    private static final Logger log = LoggerFactory.getLogger(SlackPersonErasureService.class);

    private final SlackMessageRepository messageRepository;
    private final SlackThreadRepository threadRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    public SlackPersonErasureService(
        SlackMessageRepository messageRepository,
        SlackThreadRepository threadRepository,
        ConversationFeedbackErasure conversationFeedbackErasure
    ) {
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.conversationFeedbackErasure = conversationFeedbackErasure;
    }

    /**
     * Erase everything this workspace stores about one member. Idempotent (a member with nothing stored deletes 0
     * rows); every statement carries the {@code workspace_id} predicate and is narrowed to this member, so a
     * co-participant's messages, another workspace's rows, and PR/ISSUE feedback are all left intact.
     *
     * @param workspaceId the tenant the erasure is scoped to
     * @param memberId    the resolved workspace {@code User} id (the {@code author_member_id} / participant id)
     * @param slackUserId the person's Slack id (for the audit log; the deletes key on {@code memberId})
     */
    @Transactional
    public void eraseMember(long workspaceId, long memberId, @Nullable String slackUserId) {
        int messages = messageRepository.deleteByWorkspaceIdAndAuthorMemberId(workspaceId, memberId);
        int threadsPruned = threadRepository.pruneParticipant(workspaceId, memberId);
        int derived = conversationFeedbackErasure.eraseConversationFeedbackAboutUser(workspaceId, memberId);
        log.info(
            "Slack person erasure: workspace={} member={} slackUser={} → messages={} threadsPruned={} derivedRows={}",
            workspaceId,
            memberId,
            slackUserId,
            messages,
            threadsPruned,
            derived
        );
    }
}

package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorTurnRatingRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk-deletes every Slack-owned, workspace-scoped table when a workspace is purged.
 *
 * <p>{@code WorkspaceStatus.PURGED} is a soft delete, so an {@code ON DELETE CASCADE} on
 * {@code workspace_id} would not fire — each module must drop its own rows explicitly. This
 * contributor covers the six Slack tables: {@code slack_message} (PII-bearing content),
 * {@code slack_thread}, {@code slack_monitored_channel}, {@code mentor_slack_thread},
 * {@code mentor_turn_rating} (the feedback-button ratings), and {@code slack_participant_consent}
 * (per-person opt-out records — cleared with the tenant they belong to).
 *
 * <p>{@link #getOrder()} returns {@value #PURGE_ORDER} so this runs <b>before</b>
 * {@code ConnectionPurgeContributor} ({@code -100}, which transitions the Slack Connection to
 * UNINSTALLED and clears its token) and well before the default-{@code 0} contributors that run
 * after workspace teardown. Deleting content while the Connection is still intact keeps the purge
 * ordering auditable and avoids leaving orphaned content behind a torn-down Connection.
 *
 * <p>No {@code @Transactional} here: {@code WorkspaceLifecycleService#purgeWorkspace} already runs
 * the whole contributor chain inside one transaction, and each derived {@code deleteByWorkspaceId}
 * carries the {@code workspace_id} predicate the tenancy inspector requires.
 *
 * <p><b>Derived CONVERSATION rows.</b> Before dropping {@code slack_thread} (the artifact the derived rows point
 * at) this contributor erases the workspace's {@code CONVERSATION_THREAD} observations/feedback through the
 * practices {@link ConversationFeedbackErasure} port. The {@code PracticesWorkspacePurgeContributor} also clears
 * all practice rows for the workspace, but the two are ordering-independent: whichever runs first, the other's
 * call is an idempotent no-op. Making the call explicit here decouples correctness from that ordering.
 */
@Component
@RequiredArgsConstructor
public class SlackWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    /** Runs before {@code ConnectionPurgeContributor} ({@code -100}); must be strictly less than it. */
    static final int PURGE_ORDER = -200;

    private final SlackMessageRepository slackMessageRepository;
    private final SlackThreadRepository slackThreadRepository;
    private final SlackMonitoredChannelRepository slackMonitoredChannelRepository;
    private final MentorSlackThreadRepository mentorSlackThreadRepository;
    private final MentorTurnRatingRepository mentorTurnRatingRepository;
    private final SlackParticipantConsentRepository slackParticipantConsentRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Erase the derived CONVERSATION_THREAD observations/feedback before the slack_thread aggregates they point
        // at are dropped. Scoped to CONVERSATION_THREAD + this workspace; idempotent, so a double purge is a no-op.
        conversationFeedbackErasure.eraseAllConversationForWorkspace(workspaceId);
        slackMessageRepository.deleteByWorkspaceId(workspaceId);
        slackThreadRepository.deleteByWorkspaceId(workspaceId);
        slackMonitoredChannelRepository.deleteByWorkspaceId(workspaceId);
        mentorSlackThreadRepository.deleteByWorkspaceId(workspaceId);
        mentorTurnRatingRepository.deleteByWorkspaceId(workspaceId);
        slackParticipantConsentRepository.deleteByWorkspaceId(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}

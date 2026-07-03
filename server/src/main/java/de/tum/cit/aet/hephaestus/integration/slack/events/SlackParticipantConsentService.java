package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a person's Slack consent decision (the write side of {@link SlackParticipantConsentGate}). Keyed by
 * {@code (workspace, slack_user_id)} and member-optional by design: an unlinked user's opt-out is still stored so it
 * applies once they later link.
 *
 * <p>An App Home decision is a single toggle that drives BOTH consent bits — opting out sets
 * {@code ingestion_opted_out} (stops future ingestion) and {@code research_opted_out} (persisted alongside; research
 * semantics unchanged in this slice); opting back in clears both. It never un-erases already-collected data — the
 * opt-out's erasure of past data is a separate, irreversible act.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackParticipantConsentService {

    /** Provenance stamped on an App Home consent decision (fits {@code source VARCHAR(32)}). */
    static final String SOURCE_SLACK_APP_HOME = "SLACK_APP_HOME";

    private final SlackParticipantConsentRepository participantConsentRepository;

    public SlackParticipantConsentService(SlackParticipantConsentRepository participantConsentRepository) {
        this.participantConsentRepository = participantConsentRepository;
    }

    /**
     * Persist an App Home consent decision for one Slack user in one workspace. {@code optIn == false} (opt out)
     * sets both consent bits; {@code optIn == true} clears them. A blank Slack user id is a no-op.
     */
    @Transactional
    public void recordAppHomeDecision(long workspaceId, String slackUserId, boolean optIn) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        boolean optedOut = !optIn;
        participantConsentRepository.upsert(workspaceId, slackUserId, optedOut, optedOut, SOURCE_SLACK_APP_HOME);
    }
}

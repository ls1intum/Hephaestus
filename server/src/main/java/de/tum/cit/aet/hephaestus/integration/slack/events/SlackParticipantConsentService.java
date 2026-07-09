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
 * <p>The two bits are intentionally separate: channel-message controls change {@code ingestion_opted_out}; research
 * controls change {@code research_opted_out}. A channel-message opt-out may also erase already-collected channel
 * data, but that erasure is performed by the caller through {@code SlackPersonErasureService}.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackParticipantConsentService {

    /** Provenance stamped on an App Home consent decision (fits {@code source VARCHAR(32)}). */
    static final String SOURCE_SLACK_APP_HOME = "SLACK_APP_HOME";

    /** Provenance stamped when a member uses the in-channel notice button. */
    static final String SOURCE_SLACK_CHANNEL_NOTICE = "SLACK_CHANNEL_NOTICE";

    private final SlackParticipantConsentRepository participantConsentRepository;

    public SlackParticipantConsentService(SlackParticipantConsentRepository participantConsentRepository) {
        this.participantConsentRepository = participantConsentRepository;
    }

    /**
     * Legacy combined App Home decision. Kept only for older tests/callers; new UI should call the focused methods
     * below so message use and research participation cannot drift from the labels.
     */
    @Transactional
    public void recordAppHomeDecision(long workspaceId, String slackUserId, boolean optIn) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        boolean optedOut = !optIn;
        participantConsentRepository.upsert(workspaceId, slackUserId, optedOut, optedOut, SOURCE_SLACK_APP_HOME);
    }

    /**
     * Persist an ingestion-only opt-out from the in-channel notice. This excludes the person's monitored-channel
     * messages but deliberately does not change research participation.
     */
    @Transactional
    public void recordChannelMessageOptOut(long workspaceId, String slackUserId) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        participantConsentRepository.optOutOfIngestion(workspaceId, slackUserId, SOURCE_SLACK_CHANNEL_NOTICE);
    }

    @Transactional
    public void recordChannelMessageOptIn(long workspaceId, String slackUserId) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        participantConsentRepository.optInToIngestion(workspaceId, slackUserId, SOURCE_SLACK_APP_HOME);
    }

    @Transactional
    public void recordResearchDecision(long workspaceId, String slackUserId, boolean participate) {
        if (slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        participantConsentRepository.setResearchOptOut(workspaceId, slackUserId, !participate, SOURCE_SLACK_APP_HOME);
    }
}

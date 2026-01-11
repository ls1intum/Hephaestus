package de.tum.in.www1.hephaestus.practices.feedback;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import com.langfuse.client.resources.score.types.CreateScoreResponse;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.practices.dto.BadPracticeFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedback;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing bad practice feedback and resolution.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Recording user resolutions (FIXED, WONT_FIX, WRONG) for detected bad practices</li>
 *   <li>Storing detailed feedback explanations for analysis</li>
 *   <li>Forwarding feedback to Langfuse for LLM observability and model improvement</li>
 * </ul>
 *
 * <h2>Langfuse Integration</h2>
 * <p>When tracing is enabled, feedback is sent asynchronously to Langfuse using the
 * trace ID captured during detection. This creates a feedback loop for improving
 * the AI detection model.
 *
 * @see PullRequestBadPractice#getDetectionTraceId() Trace ID for Langfuse correlation
 */
@Service
@Transactional
public class BadPracticeFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(BadPracticeFeedbackService.class);

    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final BadPracticeFeedbackRepository badPracticeFeedbackRepository;
    private final LangfuseClient langfuseClient;
    private final boolean tracingEnabled;

    public BadPracticeFeedbackService(
        PullRequestBadPracticeRepository pullRequestBadPracticeRepository,
        BadPracticeFeedbackRepository badPracticeFeedbackRepository,
        @Value("${hephaestus.detection.tracing.enabled}") boolean tracingEnabled,
        @Value("${hephaestus.detection.tracing.host}") String tracingHost,
        @Value("${hephaestus.detection.tracing.public-key}") String tracingPublicKey,
        @Value("${hephaestus.detection.tracing.secret-key}") String tracingSecretKey
    ) {
        this.pullRequestBadPracticeRepository = pullRequestBadPracticeRepository;
        this.badPracticeFeedbackRepository = badPracticeFeedbackRepository;
        this.tracingEnabled = tracingEnabled;

        // Initialize Langfuse client once if tracing is enabled (connection reuse)
        if (tracingEnabled && !tracingHost.isBlank() && !tracingPublicKey.isBlank()) {
            this.langfuseClient = LangfuseClient.builder()
                .url(tracingHost)
                .credentials(tracingPublicKey, tracingSecretKey)
                .build();
        } else {
            this.langfuseClient = null;
        }
    }

    /**
     * Resolves a bad practice by updating its user state.
     *
     * @param workspace   the workspace context
     * @param badPractice the bad practice to resolve
     * @param state       the new state (must be a valid user resolution state)
     * @see PullRequestBadPracticeState#USER_RESOLUTION_STATES valid states
     */
    public void resolveBadPractice(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        PullRequestBadPracticeState state
    ) {
        log.info(
            "Resolving bad practice {} from {} to {} in workspace {}",
            badPractice.getId(),
            badPractice.getUserState(),
            state,
            workspace.getWorkspaceSlug()
        );
        badPractice.setUserState(state);
        pullRequestBadPracticeRepository.save(badPractice);
    }

    /**
     * Records user feedback for a bad practice and optionally sends it to Langfuse.
     *
     * @param workspace   the workspace context
     * @param badPractice the bad practice receiving feedback
     * @param feedback    the feedback details (type and explanation)
     */
    public void provideFeedback(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        BadPracticeFeedbackDTO feedback
    ) {
        log.info(
            "Recording feedback for bad practice {} in workspace {}",
            badPractice.getId(),
            workspace.getWorkspaceSlug()
        );

        BadPracticeFeedback badPracticeFeedback = new BadPracticeFeedback();
        badPracticeFeedback.setPullRequestBadPractice(badPractice);
        badPracticeFeedback.setExplanation(feedback.explanation());
        badPracticeFeedback.setType(feedback.type());
        badPracticeFeedback.setCreatedAt(Instant.now());
        badPracticeFeedbackRepository.save(badPracticeFeedback);

        if (tracingEnabled && badPractice.getDetectionTraceId() != null) {
            sendFeedbackToLangfuse(badPractice, feedback);
        }
    }

    /**
     * Sends feedback to Langfuse asynchronously for LLM observability.
     *
     * <p>This method is public to allow Spring's @Async proxy to work correctly.
     * Failures are logged but not propagated to avoid disrupting the main feedback flow.
     *
     * @param badPractice the bad practice with detection trace ID
     * @param feedback    the user's feedback
     */
    @Async
    @Transactional(readOnly = true) // No DB writes, just reading for async context
    public void sendFeedbackToLangfuse(PullRequestBadPractice badPractice, BadPracticeFeedbackDTO feedback) {
        if (langfuseClient == null) {
            log.debug("Langfuse client not configured, skipping feedback submission");
            return;
        }

        log.info("Sending feedback to Langfuse for bad practice: {}", badPractice.getId());
        try {
            CreateScoreRequest request = CreateScoreRequest.builder()
                .traceId(badPractice.getDetectionTraceId())
                .name("user_feedback")
                .value(CreateScoreValue.of(feedback.type()))
                .comment(
                    String.format("Bad practice: %s - Feedback: %s", badPractice.getTitle(), feedback.explanation())
                )
                .build();

            CreateScoreResponse response = langfuseClient.score().create(request);
            log.info("Feedback sent to Langfuse successfully (id={})", response.getId());
        } catch (RuntimeException e) {
            // Log with context but don't propagate - Langfuse is optional observability
            log.error(
                "Failed to send feedback to Langfuse for bad practice {}: {}",
                badPractice.getId(),
                e.getMessage()
            );
        }
    }
}

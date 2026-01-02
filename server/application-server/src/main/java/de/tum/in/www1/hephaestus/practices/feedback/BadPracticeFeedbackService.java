package de.tum.in.www1.hephaestus.practices.feedback;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import com.langfuse.client.resources.score.types.CreateScoreResponse;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedback;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for managing bad practice feedback and resolution.
 * Handles user feedback on detected bad practices and integrates with Langfuse
 * for LLM observability when tracing is enabled.
 */
@Service
public class BadPracticeFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeFeedbackService.class);

    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final BadPracticeFeedbackRepository badPracticeFeedbackRepository;
    private final boolean tracingEnabled;
    private final String tracingHost;
    private final String tracingPublicKey;
    private final String tracingSecretKey;

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
        this.tracingHost = tracingHost;
        this.tracingPublicKey = tracingPublicKey;
        this.tracingSecretKey = tracingSecretKey;
    }

    /**
     * Resolves a bad practice by updating its user state.
     *
     * @param workspace the workspace context
     * @param badPractice the bad practice to resolve
     * @param state the new state (FIXED, WONT_FIX, or WRONG)
     */
    public void resolveBadPractice(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        PullRequestBadPracticeState state
    ) {
        logger.info(
            "Resolving bad practice {} with state {} in workspace {}",
            badPractice.getId(),
            state,
            workspace.getWorkspaceSlug()
        );
        badPractice.setUserState(state);
        pullRequestBadPracticeRepository.save(badPractice);
    }

    /**
     * Records user feedback for a bad practice and optionally sends it to Langfuse.
     *
     * @param workspace the workspace context
     * @param badPractice the bad practice receiving feedback
     * @param feedback the feedback details
     */
    public void provideFeedback(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        BadPracticeFeedbackDTO feedback
    ) {
        logger.info(
            "Providing feedback for bad practice {} in workspace {}",
            badPractice.getId(),
            workspace.getWorkspaceSlug()
        );

        BadPracticeFeedback badPracticeFeedback = new BadPracticeFeedback();
        badPracticeFeedback.setPullRequestBadPractice(badPractice);
        badPracticeFeedback.setExplanation(feedback.explanation());
        badPracticeFeedback.setType(feedback.type());
        badPracticeFeedback.setCreationTime(Instant.now());
        badPracticeFeedbackRepository.save(badPracticeFeedback);

        if (tracingEnabled && badPractice.getDetectionTraceId() != null) {
            sendFeedbackToLangfuse(badPractice, feedback);
        }
    }

    /**
     * Sends feedback to Langfuse asynchronously for LLM observability.
     * This method is public to allow Spring's @Async proxy to work correctly.
     */
    @Async
    public void sendFeedbackToLangfuse(PullRequestBadPractice badPractice, BadPracticeFeedbackDTO feedback) {
        logger.info("Sending feedback to Langfuse for bad practice: {}", badPractice.getId());
        try {
            LangfuseClient client = LangfuseClient.builder()
                .url(tracingHost)
                .credentials(tracingPublicKey, tracingSecretKey)
                .build();

            CreateScoreRequest request = CreateScoreRequest.builder()
                .traceId(badPractice.getDetectionTraceId())
                .name("user_feedback")
                .value(CreateScoreValue.of(feedback.type()))
                .comment(
                    String.format("Bad practice: %s - Feedback: %s", badPractice.getTitle(), feedback.explanation())
                )
                .build();

            CreateScoreResponse response = client.score().create(request);
            logger.info("Feedback sent to Langfuse: {}", response.toString());
        } catch (Exception e) {
            logger.error("Failed to send feedback to Langfuse: {}", e.getMessage());
        }
    }
}

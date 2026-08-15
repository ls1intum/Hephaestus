package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The operator surface may see that a reflection message exists; it may not read it.
 *
 * <p>In a course deployment the workspace admin is the instructor, and a REFLECTION body is the only
 * feedback text whose sole audience is the developer it is about — in-context bodies are already public
 * on the pull request, conversational ones are NULL until the mentor speaks them. This is the assertion
 * that stops the detail route from becoming a window into a student's private page.
 */
class ReviewFeedbackReflectionBodyTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final FeedbackObservationRepository feedbackObservationRepository = mock(
        FeedbackObservationRepository.class
    );
    private final FeedbackPlacementRepository feedbackPlacementRepository = mock(FeedbackPlacementRepository.class);
    private final ReviewSubjectResolver subjectResolver = mock(ReviewSubjectResolver.class);
    private final ReviewArtifactResolver artifactResolver = mock(ReviewArtifactResolver.class);

    private final ReviewFeedbackQueryService service = new ReviewFeedbackQueryService(
        feedbackRepository,
        feedbackObservationRepository,
        feedbackPlacementRepository,
        subjectResolver,
        artifactResolver
    );

    @Test
    void withholdsAReflectionBodyFromTheOperatorDetailRoute() {
        ReviewFeedbackDetailDTO detail = detailFor(FeedbackChannel.REFLECTION);

        assertThat(detail.body()).isNull();
        // Everything needed to audit the pipeline still travels — only the words do not.
        assertThat(detail.channel()).isEqualTo(FeedbackChannel.REFLECTION);
        assertThat(detail.deliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
    }

    @Test
    void stillReturnsTheBodyOnTheLanesTheDeveloperCanAlreadyBeReadOn() {
        assertThat(detailFor(FeedbackChannel.IN_CONTEXT).body()).isEqualTo("the composed text");
    }

    private ReviewFeedbackDetailDTO detailFor(FeedbackChannel channel) {
        UUID feedbackId = UUID.randomUUID();
        Feedback unit = Feedback.builder()
            .id(feedbackId)
            .agentJobId(UUID.randomUUID())
            .workspaceId(WORKSPACE_ID)
            .recipientUserId(11L)
            .aboutUserId(11L)
            .channel(channel)
            .position(0)
            .deliveryState(FeedbackDeliveryState.PREPARED)
            .source(FeedbackSource.AGENT)
            .body("the composed text")
            .createdAt(Instant.parse("2026-08-15T12:00:00Z"))
            .build();
        when(feedbackRepository.findByIdAndWorkspaceId(feedbackId, WORKSPACE_ID)).thenReturn(Optional.of(unit));
        when(feedbackObservationRepository.findBoundObservations(WORKSPACE_ID, feedbackId)).thenReturn(List.of());
        when(feedbackPlacementRepository.findByFeedbackIdInDisplayOrder(feedbackId)).thenReturn(List.of());
        when(subjectResolver.resolve(any())).thenReturn(Map.of());
        // The unit is unanchored (a reflection message is about several pieces of work, not one), so the
        // artifact resolver is never reached — deliberately left unstubbed to keep that visible.
        return service.get(WORKSPACE_ID, feedbackId);
    }
}

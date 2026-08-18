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
 * The operator surface may see that a private message exists; it may not read it.
 *
 * <p>In a course deployment the workspace admin is the instructor, and two lanes carry text whose
 * audience is not them. An IN_APP body is the developer's own page. An IN_CHAT body is the
 * composer's coaching move for the mentor, and its evidence part is a pattern claim about a named person
 * across their work, written to be shown to them alone and only once they have answered for themselves —
 * it used to be safe to return only because it was always NULL. An in-context body is already public on
 * the pull request. These are the assertions that stop the detail route from becoming a window into a
 * student's private page or into the plan for their next mentor turn.
 */
class ReviewFeedbackInAppBodyTest extends BaseUnitTest {

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
    void withholdsAnInAppBodyFromTheOperatorDetailRoute() {
        ReviewFeedbackDetailDTO detail = detailFor(FeedbackChannel.IN_APP);

        assertThat(detail.body()).isNull();
        // Everything needed to audit the pipeline still travels — only the words do not.
        assertThat(detail.channel()).isEqualTo(FeedbackChannel.IN_APP);
        assertThat(detail.deliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
    }

    @Test
    void withholdsAConversationalMoveFromTheOperatorDetailRoute() {
        ReviewFeedbackDetailDTO detail = detailFor(FeedbackChannel.IN_CHAT);

        assertThat(detail.body()).isNull();
        assertThat(detail.channel()).isEqualTo(FeedbackChannel.IN_CHAT);
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
        // The unit is unanchored (a in-app message is about several pieces of work, not one), so the
        // artifact resolver is never reached — deliberately left unstubbed to keep that visible.
        return service.get(WORKSPACE_ID, feedbackId);
    }
}

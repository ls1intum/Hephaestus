package de.tum.in.www1.hephaestus.practices.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.practices.PracticesPullRequestQueryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;
import de.tum.in.www1.hephaestus.practices.model.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

/**
 * Unit tests for PullRequestBadPracticeDetector.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Successful detection flow with mocked DetectorApi</li>
 *   <li>Error handling when DetectorApi throws exception</li>
 *   <li>PR not found scenario</li>
 *   <li>Ready-to-review label detection for lifecycle state</li>
 *   <li>Correct request construction (title, description, etc.)</li>
 *   <li>Detection result handling based on bad practices found</li>
 * </ul>
 */
@Tag("unit")
@DisplayName("PullRequestBadPracticeDetector")
@ExtendWith(MockitoExtension.class)
class PullRequestBadPracticeDetectorTest {

    private static final Long PR_ID = 100L;
    private static final Long REPO_ID = 200L;
    private static final int PR_NUMBER = 42;
    private static final String PR_TITLE = "feat: Add new feature";
    private static final String PR_BODY = "This PR adds a new feature.";
    private static final String REPO_NAME = "test-repo";
    private static final String REPO_NAME_WITH_OWNER = "owner/test-repo";
    private static final String TEMPLATE_CONTENT = "## Description\n\n## Changes";
    private static final String TRACE_ID = "trace-123";
    private static final String BAD_PRACTICE_SUMMARY = "Found some issues with this PR.";

    @Mock
    private PracticesPullRequestQueryRepository practicesPullRequestQueryRepository;

    @Mock
    private BadPracticeDetectionRepository badPracticeDetectionRepository;

    @Mock
    private PullRequestTemplateGetter pullRequestTemplateGetter;

    @Mock
    private DetectorApi detectorApi;

    @Captor
    private ArgumentCaptor<DetectorRequest> detectorRequestCaptor;

    @Captor
    private ArgumentCaptor<BadPracticeDetection> badPracticeDetectionCaptor;

    private PullRequestBadPracticeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PullRequestBadPracticeDetector(
            practicesPullRequestQueryRepository,
            badPracticeDetectionRepository,
            pullRequestTemplateGetter,
            detectorApi
        );
    }

    @Nested
    @DisplayName("detectAndSyncBadPractices by ID")
    class DetectAndSyncBadPracticesByIdTests {

        @Test
        @DisplayName("returns ERROR_NO_UPDATE_ON_PULLREQUEST when PR not found")
        void returnsErrorWhenPullRequestNotFound() {
            // Arrange
            when(practicesPullRequestQueryRepository.findById(PR_ID)).thenReturn(Optional.empty());

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(PR_ID);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST);
            verify(practicesPullRequestQueryRepository).findById(PR_ID);
            verifyNoInteractions(detectorApi);
        }

        @Test
        @DisplayName("delegates to PR-based detection when PR is found")
        void delegatesToPullRequestBasedDetection() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(practicesPullRequestQueryRepository.findById(PR_ID)).thenReturn(Optional.of(pullRequest));
            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(createEmptyDetectorResponse());
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(PR_ID);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.NO_BAD_PRACTICES_DETECTED);
            verify(detectorApi).detectBadPractices(any());
        }
    }

    @Nested
    @DisplayName("detectAndSyncBadPractices with PullRequest")
    class DetectAndSyncBadPracticesWithPullRequestTests {

        @Test
        @DisplayName("skips detection when PR has not been updated since last detection")
        void skipsDetectionWhenPullRequestNotUpdatedSinceLastDetection() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Instant lastDetection = Instant.now();
            Instant lastUpdate = lastDetection.minusSeconds(3600); // Updated 1 hour before detection
            pullRequest.setUpdatedAt(lastUpdate);

            // Create a previous detection that was done after the PR was last updated
            BadPracticeDetection previousDetection = new BadPracticeDetection();
            previousDetection.setDetectedAt(lastDetection);
            previousDetection.setBadPractices(List.of());

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(previousDetection);

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST);
            verify(detectorApi, never()).detectBadPractices(any());
        }

        @Test
        @DisplayName("runs detection when PR was updated after last detection")
        void runsDetectionWhenPullRequestUpdatedAfterLastDetection() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            Instant lastDetection = Instant.now().minusSeconds(3600);
            Instant lastUpdate = Instant.now();
            pullRequest.setUpdatedAt(lastUpdate);

            // Previous detection was before the PR update
            BadPracticeDetection previousDetection = new BadPracticeDetection();
            previousDetection.setDetectedAt(lastDetection);
            previousDetection.setBadPractices(List.of());
            previousDetection.setSummary("");

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(previousDetection);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(createEmptyDetectorResponse());
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.NO_BAD_PRACTICES_DETECTED);
            verify(detectorApi).detectBadPractices(any());
        }

        @Test
        @DisplayName("runs detection when PR has never been detected before")
        void runsDetectionWhenPullRequestNeverDetectedBefore() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            pullRequest.setUpdatedAt(Instant.now());

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(createEmptyDetectorResponse());
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.NO_BAD_PRACTICES_DETECTED);
            verify(detectorApi).detectBadPractices(any());
        }
    }

    @Nested
    @DisplayName("Successful Detection Flow")
    class SuccessfulDetectionFlowTests {

        @Test
        @DisplayName("returns BAD_PRACTICES_DETECTED when bad practices are found")
        void returnsBadPracticesDetectedWhenBadPracticesFound() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            DetectorResponse response = createDetectorResponseWithBadPractices();

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(response);
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.BAD_PRACTICES_DETECTED);
        }

        @Test
        @DisplayName("returns NO_BAD_PRACTICES_DETECTED when no bad practices are found")
        void returnsNoBadPracticesDetectedWhenNoBadPracticesFound() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(createEmptyDetectorResponse());
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            assertThat(result).isEqualTo(DetectionResult.NO_BAD_PRACTICES_DETECTED);
        }

        @Test
        @DisplayName("saves detection result to repository")
        void savesDetectionResultToRepository() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            DetectorResponse response = createDetectorResponseWithBadPractices();

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(response);
            when(badPracticeDetectionRepository.save(badPracticeDetectionCaptor.capture())).thenAnswer(inv ->
                inv.getArgument(0)
            );

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            BadPracticeDetection savedDetection = badPracticeDetectionCaptor.getValue();
            assertThat(savedDetection.getPullRequest()).isEqualTo(pullRequest);
            assertThat(savedDetection.getSummary()).isEqualTo(BAD_PRACTICE_SUMMARY);
            assertThat(savedDetection.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(savedDetection.getBadPractices()).hasSize(1);
        }

        @Test
        @DisplayName("stores detection time and summary in BadPracticeDetection")
        void storesDetectionTimeAndSummaryInBadPracticeDetection() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            DetectorResponse response = createDetectorResponseWithBadPractices();

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(response);
            when(badPracticeDetectionRepository.save(badPracticeDetectionCaptor.capture())).thenAnswer(inv ->
                inv.getArgument(0)
            );

            Instant beforeDetection = Instant.now();

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            BadPracticeDetection savedDetection = badPracticeDetectionCaptor.getValue();
            assertThat(savedDetection.getDetectedAt()).isAfterOrEqualTo(beforeDetection);
            assertThat(savedDetection.getSummary()).isEqualTo(BAD_PRACTICE_SUMMARY);
        }
    }

    @Nested
    @DisplayName("Request Construction")
    class RequestConstructionTests {

        @Test
        @DisplayName("constructs DetectorRequest with correct PR data")
        void constructsDetectorRequestWithCorrectPullRequestData() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getTitle()).isEqualTo(PR_TITLE);
            assertThat(capturedRequest.getDescription()).isEqualTo(PR_BODY);
            assertThat(capturedRequest.getRepositoryName()).isEqualTo(REPO_NAME);
            assertThat(capturedRequest.getPullRequestNumber().intValue()).isEqualTo(PR_NUMBER);
            assertThat(capturedRequest.getPullRequestTemplate()).isEqualTo(TEMPLATE_CONTENT);
        }

        @Test
        @DisplayName("includes previous bad practices in request")
        void includesPreviousBadPracticesInRequest() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            PullRequestBadPractice existingBadPractice = new PullRequestBadPractice();
            existingBadPractice.setTitle("Missing description");
            existingBadPractice.setDescription("PR description is empty");
            existingBadPractice.setState(PullRequestBadPracticeState.NORMAL_ISSUE);

            BadPracticeDetection previousDetection = new BadPracticeDetection();
            previousDetection.setSummary("Previous summary");
            previousDetection.setBadPractices(List.of(existingBadPractice));
            previousDetection.setDetectedAt(Instant.now().minusSeconds(7200)); // Detection was 2 hours ago

            // Set PR update time after the previous detection
            pullRequest.setUpdatedAt(Instant.now());

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(previousDetection);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getBadPractices()).hasSize(1);
            assertThat(capturedRequest.getBadPractices().get(0).getTitle()).isEqualTo("Missing description");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("returns empty detection when DetectorApi throws RestClientException")
        void returnsEmptyDetectionWhenDetectorApiThrowsRestClientException() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenThrow(new RestClientException("Connection refused"));

            // Act
            BadPracticeDetection result = detector.detectBadPracticesForPullRequest(pullRequest);

            // Assert
            assertThat(result.getBadPractices()).isEmpty();
            assertThat(result.getSummary()).isEmpty();
            assertThat(result.getPullRequest()).isEqualTo(pullRequest);
            assertThat(result.getTraceId()).isNull();
        }

        @Test
        @DisplayName("does not save pull request when DetectorApi throws exception")
        void doesNotSavePullRequestWhenDetectorApiThrowsException() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenThrow(new RestClientException("Timeout"));

            // Act
            detector.detectBadPracticesForPullRequest(pullRequest);

            // Assert
            verify(practicesPullRequestQueryRepository, never()).save(any());
            verify(badPracticeDetectionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Lifecycle State Detection")
    class LifecycleStateDetectionTests {

        @Test
        @DisplayName("detects MERGED state when PR is merged")
        void detectsMergedStateWhenPullRequestIsMerged() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            pullRequest.setMerged(true);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Merged");
        }

        @Test
        @DisplayName("detects CLOSED state when PR is closed but not merged")
        void detectsClosedStateWhenPullRequestIsClosedButNotMerged() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            pullRequest.setMerged(false);
            pullRequest.setState(Issue.State.CLOSED);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Closed");
        }

        @Test
        @DisplayName("detects DRAFT state when PR is draft")
        void detectsDraftStateWhenPullRequestIsDraft() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            pullRequest.setDraft(true);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Draft");
        }

        @Test
        @DisplayName("detects READY_TO_MERGE state when PR has ready to merge label")
        void detectsReadyToMergeStateWhenPullRequestHasReadyToMergeLabel() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            Label readyToMergeLabel = new Label();
            readyToMergeLabel.setName("ready to merge");
            pullRequest.setLabels(Set.of(readyToMergeLabel));

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Ready to merge");
        }

        @Test
        @DisplayName("detects READY_FOR_REVIEW state when PR has ready to review label")
        void detectsReadyForReviewStateWhenPullRequestHasReadyToReviewLabel() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            Label readyToReviewLabel = new Label();
            readyToReviewLabel.setName("ready to review");
            pullRequest.setLabels(Set.of(readyToReviewLabel));

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Ready for review");
        }

        @Test
        @DisplayName("detects READY_FOR_REVIEW state when PR has ready for review label")
        void detectsReadyForReviewStateWhenPullRequestHasReadyForReviewLabel() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            Label readyForReviewLabel = new Label();
            readyForReviewLabel.setName("ready for review");
            pullRequest.setLabels(Set.of(readyForReviewLabel));

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Ready for review");
        }

        @Test
        @DisplayName("detects OPEN state when PR is open without special labels")
        void detectsOpenStateWhenPullRequestIsOpenWithoutSpecialLabels() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(detectorRequestCaptor.capture())).thenReturn(
                createEmptyDetectorResponse()
            );
            when(badPracticeDetectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            DetectorRequest capturedRequest = detectorRequestCaptor.getValue();
            assertThat(capturedRequest.getLifecycleState()).isEqualTo("Open");
        }
    }

    @Nested
    @DisplayName("Bad Practice State Mapping")
    class BadPracticeStateMappingTests {

        @Test
        @DisplayName("maps CRITICAL_ISSUE status correctly")
        void mapsCriticalIssueStatusCorrectly() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);

            BadPractice badPractice = new BadPractice();
            badPractice.setTitle("Critical Issue");
            badPractice.setDescription("This is critical");
            badPractice.setStatus(BadPractice.StatusEnum.CRITICAL_ISSUE);

            DetectorResponse response = new DetectorResponse();
            response.setBadPracticeSummary(BAD_PRACTICE_SUMMARY);
            response.setBadPractices(List.of(badPractice));
            response.setTraceId(TRACE_ID);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(null);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(response);
            when(badPracticeDetectionRepository.save(badPracticeDetectionCaptor.capture())).thenAnswer(inv ->
                inv.getArgument(0)
            );

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            BadPracticeDetection savedDetection = badPracticeDetectionCaptor.getValue();
            assertThat(savedDetection.getBadPractices()).hasSize(1);
            assertThat(savedDetection.getBadPractices().get(0).getState()).isEqualTo(
                PullRequestBadPracticeState.CRITICAL_ISSUE
            );
        }

        @Test
        @DisplayName("preserves user state from existing bad practice with same title")
        void preservesUserStateFromExistingBadPracticeWithSameTitle() {
            // Arrange
            PullRequest pullRequest = createPullRequest();
            Repository repository = createRepository();
            pullRequest.setRepository(repository);
            pullRequest.setUpdatedAt(Instant.now());

            PullRequestBadPractice existingBadPractice = new PullRequestBadPractice();
            existingBadPractice.setTitle("Missing description");
            existingBadPractice.setDescription("PR description is empty");
            existingBadPractice.setState(PullRequestBadPracticeState.NORMAL_ISSUE);
            existingBadPractice.setUserState(PullRequestBadPracticeState.WONT_FIX);

            BadPracticeDetection previousDetection = new BadPracticeDetection();
            previousDetection.setSummary("Previous summary");
            previousDetection.setBadPractices(List.of(existingBadPractice));
            previousDetection.setDetectedAt(Instant.now().minusSeconds(3600));

            BadPractice newBadPractice = new BadPractice();
            newBadPractice.setTitle("Missing description");
            newBadPractice.setDescription("Updated description");
            newBadPractice.setStatus(BadPractice.StatusEnum.NORMAL_ISSUE);

            DetectorResponse response = new DetectorResponse();
            response.setBadPracticeSummary(BAD_PRACTICE_SUMMARY);
            response.setBadPractices(List.of(newBadPractice));
            response.setTraceId(TRACE_ID);

            when(badPracticeDetectionRepository.findMostRecentByPullRequestId(PR_ID)).thenReturn(previousDetection);
            when(pullRequestTemplateGetter.getPullRequestTemplate(REPO_NAME_WITH_OWNER)).thenReturn(TEMPLATE_CONTENT);
            when(detectorApi.detectBadPractices(any())).thenReturn(response);
            when(badPracticeDetectionRepository.save(badPracticeDetectionCaptor.capture())).thenAnswer(inv ->
                inv.getArgument(0)
            );

            // Act
            detector.detectAndSyncBadPractices(pullRequest);

            // Assert
            BadPracticeDetection savedDetection = badPracticeDetectionCaptor.getValue();
            assertThat(savedDetection.getBadPractices()).hasSize(1);
            assertThat(savedDetection.getBadPractices().get(0).getUserState()).isEqualTo(
                PullRequestBadPracticeState.WONT_FIX
            );
        }
    }

    // ==================== Helper Methods ====================

    private PullRequest createPullRequest() {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setId(PR_ID);
        pullRequest.setNumber(PR_NUMBER);
        pullRequest.setTitle(PR_TITLE);
        pullRequest.setBody(PR_BODY);
        pullRequest.setState(Issue.State.OPEN);
        pullRequest.setMerged(false);
        pullRequest.setDraft(false);
        pullRequest.setLabels(new HashSet<>());
        return pullRequest;
    }

    private Repository createRepository() {
        Repository repository = new Repository();
        repository.setId(REPO_ID);
        repository.setName(REPO_NAME);
        repository.setNameWithOwner(REPO_NAME_WITH_OWNER);
        return repository;
    }

    private DetectorResponse createEmptyDetectorResponse() {
        DetectorResponse response = new DetectorResponse();
        response.setBadPracticeSummary("");
        response.setBadPractices(List.of());
        response.setTraceId(TRACE_ID);
        return response;
    }

    private DetectorResponse createDetectorResponseWithBadPractices() {
        BadPractice badPractice = new BadPractice();
        badPractice.setTitle("Missing description");
        badPractice.setDescription("The PR description is empty or insufficient.");
        badPractice.setStatus(BadPractice.StatusEnum.NORMAL_ISSUE);

        DetectorResponse response = new DetectorResponse();
        response.setBadPracticeSummary(BAD_PRACTICE_SUMMARY);
        response.setBadPractices(List.of(badPractice));
        response.setTraceId(TRACE_ID);
        return response;
    }
}

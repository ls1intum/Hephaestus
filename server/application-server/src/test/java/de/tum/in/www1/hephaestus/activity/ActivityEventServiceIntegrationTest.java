package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for ActivityEventService.
 *
 * <p>Tests verify database-level behavior including:
 * <ul>
 *   <li>Idempotency via unique constraint on event_key</li>
 *   <li>XP clamping and precision rounding</li>
 *   <li>Relationship to workspace, user, and repository entities</li>
 *   <li>Cache eviction behavior</li>
 * </ul>
 */
@DisplayName("ActivityEventService Integration")
@Transactional
class ActivityEventServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ActivityEventService activityEventService;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    private Workspace testWorkspace;
    private User testUser;
    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("test-workspace");
        testWorkspace.setDisplayName("Test Workspace");
        testWorkspace.setAccountLogin("test-org");
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);

        // Create user
        testUser = new User();
        testUser.setLogin("test-user");
        testUser.setAvatarUrl("https://example.com/avatar.png");
        testUser = userRepository.save(testUser);

        // Create repository
        testRepository = new Repository();
        testRepository.setName("test-repo");
        testRepository.setNameWithOwner("test-org/test-repo");
        testRepository.setHtmlUrl("https://github.com/test-org/test-repo");
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository = repositoryRepository.save(testRepository);
    }

    @Nested
    @DisplayName("Event Recording")
    class EventRecording {

        @Test
        @DisplayName("persists event with all fields correctly")
        void persistsEventWithAllFields() {
            Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
            Map<String, Object> payload = Map.of("prNumber", 42, "title", "Test PR");

            boolean result = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                occurredAt,
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                100L,
                1.5,
                SourceSystem.GITHUB,
                payload
            );

            assertThat(result).isTrue();

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);

            ActivityEvent event = events.get(0);
            assertThat(event.getEventType()).isEqualTo(ActivityEventType.PULL_REQUEST_OPENED);
            assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
            assertThat(event.getActor().getId()).isEqualTo(testUser.getId());
            assertThat(event.getRepository().getId()).isEqualTo(testRepository.getId());
            assertThat(event.getWorkspace().getId()).isEqualTo(testWorkspace.getId());
            assertThat(event.getTargetType()).isEqualTo(ActivityTargetType.PULL_REQUEST.getValue());
            assertThat(event.getTargetId()).isEqualTo(100L);
            assertThat(event.getXp()).isEqualTo(1.5);
            assertThat(event.getSourceSystem()).isEqualTo(SourceSystem.GITHUB.getValue());
            assertThat(event.getPayload()).containsEntry("prNumber", 42);
            assertThat(event.getSchemaVersion()).isEqualTo(ActivityEventService.CURRENT_SCHEMA_VERSION);
        }

        @Test
        @DisplayName("persists event without optional fields")
        void persistsEventWithoutOptionalFields() {
            boolean result = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.ISSUE_CREATED,
                Instant.now(),
                null, // No actor (system event)
                null, // No repository
                ActivityTargetType.ISSUE,
                200L,
                0.0,
                SourceSystem.SYSTEM
            );

            assertThat(result).isTrue();

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getActor()).isNull();
            assertThat(events.get(0).getRepository()).isNull();
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("rejects duplicate events with same event key")
        void rejectsDuplicateEvents() {
            Instant occurredAt = Instant.now();

            // First record - should succeed
            boolean first = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                occurredAt,
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                100L,
                1.0,
                SourceSystem.GITHUB
            );

            // Second record with same key - should be rejected
            boolean second = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                occurredAt,
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                100L,
                1.0,
                SourceSystem.GITHUB
            );

            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(activityEventRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("allows same event type with different timestamps")
        void allowsSameEventTypeWithDifferentTimestamps() {
            Instant time1 = Instant.parse("2024-01-15T10:00:00Z");
            Instant time2 = Instant.parse("2024-01-15T11:00:00Z");

            boolean first = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.REVIEW_APPROVED,
                time1,
                testUser,
                testRepository,
                ActivityTargetType.REVIEW,
                100L,
                2.0,
                SourceSystem.GITHUB
            );

            boolean second = activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.REVIEW_APPROVED,
                time2,
                testUser,
                testRepository,
                ActivityTargetType.REVIEW,
                100L,
                2.5,
                SourceSystem.GITHUB
            );

            assertThat(first).isTrue();
            assertThat(second).isTrue();
            assertThat(activityEventRepository.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("XP Handling")
    class XpHandling {

        @Test
        @DisplayName("clamps negative XP to zero")
        void clampsNegativeXpToZero() {
            activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                Instant.now(),
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                100L,
                -50.0,
                SourceSystem.GITHUB
            );

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getXp()).isZero();
        }

        @Test
        @DisplayName("rounds XP to 2 decimal places using HALF_UP")
        void roundsXpTo2DecimalPlaces() {
            activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.REVIEW_APPROVED,
                Instant.now(),
                testUser,
                testRepository,
                ActivityTargetType.REVIEW,
                100L,
                3.14159, // Should be rounded to 3.14
                SourceSystem.GITHUB
            );

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getXp()).isEqualTo(3.14);
        }

        @Test
        @DisplayName("rounds XP correctly with HALF_UP (0.005 → 0.01)")
        void roundsXpWithHalfUp() {
            activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.REVIEW_CHANGES_REQUESTED,
                Instant.now(),
                testUser,
                testRepository,
                ActivityTargetType.REVIEW,
                101L, // Different target to avoid duplicate
                2.555, // Should round UP to 2.56 (HALF_UP)
                SourceSystem.GITHUB
            );

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getXp()).isEqualTo(2.56);
        }
    }

    @Nested
    @DisplayName("Workspace Validation")
    class WorkspaceValidation {

        @Test
        @DisplayName("returns false when workspace does not exist")
        void returnsFalseForNonExistentWorkspace() {
            boolean result = activityEventService.record(
                999999L, // Non-existent workspace ID
                ActivityEventType.PULL_REQUEST_OPENED,
                Instant.now(),
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                100L,
                1.0,
                SourceSystem.GITHUB
            );

            assertThat(result).isFalse();
            assertThat(activityEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Event Key Generation")
    class EventKeyGeneration {

        @Test
        @DisplayName("generates correct event key format")
        void generatesCorrectEventKey() {
            Instant occurredAt = Instant.parse("2024-01-15T10:30:00.000Z");

            activityEventService.record(
                testWorkspace.getId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                occurredAt,
                testUser,
                testRepository,
                ActivityTargetType.PULL_REQUEST,
                42L,
                1.0,
                SourceSystem.GITHUB
            );

            List<ActivityEvent> events = activityEventRepository.findAll();
            assertThat(events).hasSize(1);

            String expectedKey = String.format("pr.opened:42:%d", occurredAt.toEpochMilli());
            assertThat(events.get(0).getEventKey()).isEqualTo(expectedKey);
        }
    }
}

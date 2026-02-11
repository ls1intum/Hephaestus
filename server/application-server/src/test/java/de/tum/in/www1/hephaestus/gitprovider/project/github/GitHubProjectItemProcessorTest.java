package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link GitHubProjectItemProcessor}.
 * <p>
 * Covers the core processing pipeline ({@code process()}, {@code processArchived()},
 * {@code processRestored()}), stale item removal logic ({@code removeStaleIssuePrItems()},
 * {@code removeStaleDraftIssues()}), and edge cases around null inputs and unknown content types.
 */
@DisplayName("GitHubProjectItemProcessor")
class GitHubProjectItemProcessorTest extends BaseUnitTest {

    @Mock
    private ProjectItemRepository projectItemRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private GitHubUserProcessor gitHubUserProcessor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GitHubProjectItemProcessor processor;

    private static final Long SCOPE_ID = 100L;
    private static final Long PROJECT_ID = 42L;
    private static final String NODE_ID = "PVTI_abc123";
    private static final Long ITEM_DB_ID = 9001L;

    private Project project;
    private ProcessingContext context;

    @BeforeEach
    void setUp() {
        processor = new GitHubProjectItemProcessor(
            projectItemRepository,
            issueRepository,
            userRepository,
            labelRepository,
            milestoneRepository,
            gitHubUserProcessor,
            eventPublisher
        );

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("Test Project");
        project.setOwnerType(Project.OwnerType.ORGANIZATION);
        project.setOwnerId(1L);
        project.setNumber(1);
        project.setUrl("https://github.com/orgs/test/projects/1");

        context = ProcessingContext.forSync(SCOPE_ID, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private GitHubProjectItemDTO createDraftIssueDTO() {
        return new GitHubProjectItemDTO(
            null,
            ITEM_DB_ID,
            NODE_ID,
            null,
            "DRAFT_ISSUE",
            null,
            null,
            "Draft Title",
            "Draft Body",
            false,
            null,
            Collections.emptyList(),
            false,
            0,
            null,
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-01-02T00:00:00Z")
        );
    }

    private GitHubProjectItemDTO createIssueDTO(Long issueId) {
        return new GitHubProjectItemDTO(
            null,
            ITEM_DB_ID,
            NODE_ID,
            null,
            "ISSUE",
            issueId,
            42,
            null,
            null,
            false,
            null,
            Collections.emptyList(),
            false,
            0,
            null,
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-01-02T00:00:00Z")
        );
    }

    private GitHubProjectItemDTO createPullRequestDTO(Long issueId) {
        return new GitHubProjectItemDTO(
            null,
            ITEM_DB_ID,
            NODE_ID,
            null,
            "PULL_REQUEST",
            issueId,
            99,
            null,
            null,
            false,
            null,
            Collections.emptyList(),
            false,
            0,
            null,
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-01-02T00:00:00Z")
        );
    }

    private ProjectItem createProjectItemEntity(ProjectItem.ContentType contentType) {
        ProjectItem item = new ProjectItem();
        item.setId(ITEM_DB_ID);
        item.setNodeId(NODE_ID);
        item.setProject(project);
        item.setContentType(contentType);
        item.setArchived(false);
        item.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        item.setUpdatedAt(Instant.parse("2025-01-02T00:00:00Z"));
        return item;
    }

    /**
     * Stubs repository calls for a successful process() invocation.
     *
     * @param isNew whether the item should be treated as new (not existing)
     * @param entity the entity to return from findByProjectIdAndNodeId
     */
    private void stubSuccessfulProcess(boolean isNew, ProjectItem entity) {
        when(projectItemRepository.existsByProjectIdAndNodeId(PROJECT_ID, NODE_ID)).thenReturn(!isNew);
        when(projectItemRepository.findByProjectIdAndNodeId(PROJECT_ID, NODE_ID)).thenReturn(Optional.of(entity));
    }

    // ═══════════════════════════════════════════════════════════════
    // process() tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("process")
    class Process {

        @Test
        @DisplayName("should return null when dto is null")
        void shouldReturnNullWhenDtoIsNull() {
            // Act
            ProjectItem result = processor.process(null, project, context);

            // Assert
            assertThat(result).isNull();
            verify(projectItemRepository, never()).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should return null when nodeId is null")
        void shouldReturnNullWhenNodeIdIsNull() {
            // Arrange
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                null,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                "Body",
                false,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when nodeId is blank")
        void shouldReturnNullWhenNodeIdIsBlank() {
            // Arrange
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                "  ",
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                "Body",
                false,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when databaseId is null")
        void shouldReturnNullWhenDatabaseIdIsNull() {
            // Arrange — both `id` and `databaseId` are null so getDatabaseId() returns null
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                null,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                "Body",
                false,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when content type is unknown")
        void shouldReturnNullWhenContentTypeIsUnknown() {
            // Arrange
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                NODE_ID,
                null,
                "UNKNOWN_TYPE",
                null,
                null,
                null,
                null,
                false,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNull();
            verify(projectItemRepository, never()).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should process DRAFT_ISSUE without issue lookup and publish created event")
        void shouldProcessDraftIssueWithoutIssueLookup() {
            // Arrange
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setDraftTitle("Draft Title");
            entity.setDraftBody("Draft Body");
            stubSuccessfulProcess(true, entity);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ITEM_DB_ID);
            assertThat(result.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);

            // Verify upsert was called with correct params
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(), // issueId — not resolved for DRAFT_ISSUE
                isNull(), // contentDatabaseId — not set for DRAFT_ISSUE
                eq("Draft Title"),
                eq("Draft Body"),
                eq(false),
                isNull(), // creatorId — no creator in DTO
                eq(Instant.parse("2025-01-01T00:00:00Z")),
                eq(Instant.parse("2025-01-02T00:00:00Z"))
            );

            // Verify no issue lookup was attempted
            verify(issueRepository, never()).existsById(any());

            // Verify created event was published
            ArgumentCaptor<DomainEvent.ProjectItemCreated> eventCaptor = ArgumentCaptor.forClass(
                DomainEvent.ProjectItemCreated.class
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().projectId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("should process ISSUE item with correct content type and issue_id from contentDatabaseId")
        void shouldProcessIssueItemWithCorrectContentType() {
            // Arrange
            Long issueId = 555L;
            GitHubProjectItemDTO dto = createIssueDTO(issueId);
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.ISSUE);
            stubSuccessfulProcess(true, entity);
            when(issueRepository.existsById(issueId)).thenReturn(true);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();

            // Verify upsert was called with ISSUE content type and resolved issueId
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("ISSUE"),
                eq(issueId), // issueId resolved because issue exists locally
                eq(issueId), // contentDatabaseId set from dto.issueId()
                isNull(), // no draftTitle for ISSUE
                isNull(), // no draftBody for ISSUE
                eq(false),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should process PULL_REQUEST item with correct content type mapping")
        void shouldProcessPullRequestItemWithCorrectContentType() {
            // Arrange
            Long prIssueId = 777L;
            GitHubProjectItemDTO dto = createPullRequestDTO(prIssueId);
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.PULL_REQUEST);
            stubSuccessfulProcess(true, entity);
            when(issueRepository.existsById(prIssueId)).thenReturn(true);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("PULL_REQUEST"),
                eq(prIssueId),
                eq(prIssueId),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should set issueId to null when issue does not exist locally")
        void shouldSetIssueIdToNullWhenIssueNotLocal() {
            // Arrange
            Long nonExistentIssueId = 999L;
            GitHubProjectItemDTO dto = createIssueDTO(nonExistentIssueId);
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.ISSUE);
            stubSuccessfulProcess(true, entity);
            when(issueRepository.existsById(nonExistentIssueId)).thenReturn(false);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            // issueId should be null because issue doesn't exist locally
            // contentDatabaseId should still be set for orphan relinking
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("ISSUE"),
                isNull(), // issueId null — issue not in local DB
                eq(nonExistentIssueId), // contentDatabaseId still set for relinking
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should handle null contentDatabaseId for ISSUE item")
        void shouldHandleNullContentDatabaseIdForIssueItem() {
            // Arrange — issueId (contentDatabaseId) is null
            GitHubProjectItemDTO dto = createIssueDTO(null);
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.ISSUE);
            stubSuccessfulProcess(true, entity);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("ISSUE"),
                isNull(), // issueId null — contentDatabaseId was null
                isNull(), // contentDatabaseId null
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
            // Should not attempt issue lookup when contentDatabaseId is null
            verify(issueRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("should resolve creator when creator exists locally")
        void shouldResolveCreatorWhenCreatorExistsLocally() {
            // Arrange
            Long creatorId = 200L;
            GitHubUserDTO creatorDTO = new GitHubUserDTO(creatorId, null, "testuser", null, null, null, null);
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                "Body",
                false,
                creatorDTO,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(true, entity);
            when(userRepository.existsById(creatorId)).thenReturn(true);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(),
                isNull(),
                eq("Title"),
                eq("Body"),
                eq(false),
                eq(creatorId), // creatorId resolved
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should set creatorId to null when creator does not exist locally")
        void shouldSetCreatorIdToNullWhenCreatorNotLocal() {
            // Arrange
            Long creatorId = 300L;
            GitHubUserDTO creatorDTO = new GitHubUserDTO(creatorId, null, "unknown", null, null, null, null);
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                null,
                false,
                creatorDTO,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(true, entity);
            when(userRepository.existsById(creatorId)).thenReturn(false);

            // Act
            processor.process(dto, project, context);

            // Assert
            verify(projectItemRepository).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                isNull(), // creatorId null because user doesn't exist locally
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should set creatorId to null when creator DTO is null")
        void shouldSetCreatorIdToNullWhenCreatorDtoIsNull() {
            // Arrange
            GitHubProjectItemDTO dto = createDraftIssueDTO(); // creator is null
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(true, entity);

            // Act
            processor.process(dto, project, context);

            // Assert
            verify(projectItemRepository).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(boolean.class),
                isNull(), // creatorId null because creator DTO is null
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should pass archived flag through to upsert")
        void shouldPassArchivedFlagThroughToUpsert() {
            // Arrange
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                null,
                true,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setArchived(true);
            stubSuccessfulProcess(false, entity);

            // Act
            processor.process(dto, project, context);

            // Assert — verify archived=true is passed to upsert
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(),
                isNull(),
                eq("Title"),
                isNull(),
                eq(true), // archived = true
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should return the entity from repository after upsert")
        void shouldReturnEntityFromRepositoryAfterUpsert() {
            // Arrange
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem expectedEntity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            expectedEntity.setDraftTitle("Draft Title");
            stubSuccessfulProcess(true, expectedEntity);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isSameAs(expectedEntity);
        }

        @Test
        @DisplayName("should publish ProjectItemCreated event when item is new")
        void shouldPublishCreatedEventWhenItemIsNew() {
            // Arrange
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(true, entity); // isNew = true

            // Act
            processor.process(dto, project, context);

            // Assert
            ArgumentCaptor<DomainEvent.ProjectItemCreated> captor = ArgumentCaptor.forClass(
                DomainEvent.ProjectItemCreated.class
            );
            verify(eventPublisher).publishEvent(captor.capture());
            DomainEvent.ProjectItemCreated event = captor.getValue();
            assertThat(event.projectId()).isEqualTo(PROJECT_ID);
            assertThat(event.item().id()).isEqualTo(ITEM_DB_ID);
            assertThat(event.item().contentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
        }

        @Test
        @DisplayName("should publish ProjectItemUpdated event when item already exists")
        void shouldPublishUpdatedEventWhenItemExists() {
            // Arrange
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(false, entity); // isNew = false

            // Act
            processor.process(dto, project, context);

            // Assert
            ArgumentCaptor<DomainEvent.ProjectItemUpdated> captor = ArgumentCaptor.forClass(
                DomainEvent.ProjectItemUpdated.class
            );
            verify(eventPublisher).publishEvent(captor.capture());
            DomainEvent.ProjectItemUpdated event = captor.getValue();
            assertThat(event.projectId()).isEqualTo(PROJECT_ID);
            assertThat(event.item().id()).isEqualTo(ITEM_DB_ID);
        }

        @Test
        @DisplayName("should use fallback id field when databaseId is null but id is set")
        void shouldUseFallbackIdWhenDatabaseIdIsNull() {
            // Arrange — databaseId is null but id is set
            Long fallbackId = 5555L;
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                fallbackId,
                null,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                null,
                false,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.now(),
                Instant.now()
            );
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setId(fallbackId);
            stubSuccessfulProcess(true, entity);

            // Act
            ProjectItem result = processor.process(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            verify(projectItemRepository).upsertCore(
                eq(fallbackId), // getDatabaseId() falls back to id
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(),
                isNull(),
                eq("Title"),
                isNull(),
                eq(false),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // removeStaleDraftIssues tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeStaleDraftIssues")
    class RemoveStaleDraftIssues {

        @Test
        @DisplayName("should return 0 when projectId is null")
        void shouldReturnZeroWhenProjectIdIsNull() {
            // Act
            int result = processor.removeStaleDraftIssues(null, List.of("PVTI_1"), context);

            // Assert
            assertThat(result).isZero();
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeAndNodeIdNotIn(any(), any(), any());
            verify(projectItemRepository, never()).deleteByProjectIdAndContentType(any(), any());
        }

        @Test
        @DisplayName("should delete stale draft issues not in synced list")
        void shouldDeleteStaleDraftIssuesNotInSyncedList() {
            // Arrange
            List<String> syncedNodeIds = List.of("PVTI_1", "PVTI_2");
            when(
                projectItemRepository.deleteByProjectIdAndContentTypeAndNodeIdNotIn(
                    eq(PROJECT_ID),
                    eq(ProjectItem.ContentType.DRAFT_ISSUE),
                    eq(syncedNodeIds)
                )
            ).thenReturn(2);

            // Act
            int result = processor.removeStaleDraftIssues(PROJECT_ID, syncedNodeIds, context);

            // Assert
            assertThat(result).isEqualTo(2);
            verify(projectItemRepository).deleteByProjectIdAndContentTypeAndNodeIdNotIn(
                eq(PROJECT_ID),
                eq(ProjectItem.ContentType.DRAFT_ISSUE),
                eq(syncedNodeIds)
            );
        }

        @Test
        @DisplayName("should delete all draft issues when synced list is empty")
        void shouldDeleteAllDraftIssuesWhenSyncedListIsEmpty() {
            // Arrange
            when(
                projectItemRepository.deleteByProjectIdAndContentType(
                    eq(PROJECT_ID),
                    eq(ProjectItem.ContentType.DRAFT_ISSUE)
                )
            ).thenReturn(3);

            // Act
            int result = processor.removeStaleDraftIssues(PROJECT_ID, List.of(), context);

            // Assert
            assertThat(result).isEqualTo(3);
            verify(projectItemRepository).deleteByProjectIdAndContentType(
                eq(PROJECT_ID),
                eq(ProjectItem.ContentType.DRAFT_ISSUE)
            );
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeAndNodeIdNotIn(any(), any(), any());
        }

        @Test
        @DisplayName("should delete all draft issues when synced list is null")
        void shouldDeleteAllDraftIssuesWhenSyncedListIsNull() {
            // Arrange
            when(
                projectItemRepository.deleteByProjectIdAndContentType(
                    eq(PROJECT_ID),
                    eq(ProjectItem.ContentType.DRAFT_ISSUE)
                )
            ).thenReturn(1);

            // Act
            int result = processor.removeStaleDraftIssues(PROJECT_ID, null, context);

            // Assert
            assertThat(result).isEqualTo(1);
            verify(projectItemRepository).deleteByProjectIdAndContentType(
                eq(PROJECT_ID),
                eq(ProjectItem.ContentType.DRAFT_ISSUE)
            );
        }

        @Test
        @DisplayName("should use archived-aware repository query to preserve archived draft issues")
        void shouldUseArchivedAwareRepositoryQuery() {
            // Arrange — the repository method JPQL includes "AND archived = false"
            // so archived Draft Issues are never removed. We verify the correct
            // repository method is called, whose query definition already filters archived items.
            List<String> syncedNodeIds = List.of("PVTI_1");
            when(
                projectItemRepository.deleteByProjectIdAndContentTypeAndNodeIdNotIn(
                    eq(PROJECT_ID),
                    eq(ProjectItem.ContentType.DRAFT_ISSUE),
                    eq(syncedNodeIds)
                )
            ).thenReturn(0);

            // Act
            int result = processor.removeStaleDraftIssues(PROJECT_ID, syncedNodeIds, context);

            // Assert
            assertThat(result).isZero();
            // Key assertion: the method called is the archived-aware one
            verify(projectItemRepository).deleteByProjectIdAndContentTypeAndNodeIdNotIn(
                eq(PROJECT_ID),
                eq(ProjectItem.ContentType.DRAFT_ISSUE),
                eq(syncedNodeIds)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // removeStaleIssuePrItems tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeStaleIssuePrItems")
    class RemoveStaleIssuePrItems {

        @Test
        @DisplayName("should return 0 when projectId is null")
        void shouldReturnZeroWhenProjectIdIsNull() {
            // Act
            int result = processor.removeStaleIssuePrItems(null, List.of("PVTI_1"), context);

            // Assert
            assertThat(result).isZero();
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeInAndNodeIdNotIn(any(), any(), any());
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeIn(any(), any());
        }

        @Test
        @DisplayName("should delete non-archived items not in synced list")
        void shouldDeleteNonArchivedItemsNotInSyncedList() {
            // Arrange
            List<String> syncedNodeIds = List.of("PVTI_1", "PVTI_2");
            List<ProjectItem.ContentType> expectedTypes = List.of(
                ProjectItem.ContentType.ISSUE,
                ProjectItem.ContentType.PULL_REQUEST
            );
            when(
                projectItemRepository.deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                    eq(PROJECT_ID),
                    eq(expectedTypes),
                    eq(syncedNodeIds)
                )
            ).thenReturn(3);

            // Act
            int result = processor.removeStaleIssuePrItems(PROJECT_ID, syncedNodeIds, context);

            // Assert
            assertThat(result).isEqualTo(3);
            verify(projectItemRepository).deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                eq(PROJECT_ID),
                eq(expectedTypes),
                eq(syncedNodeIds)
            );
        }

        @Test
        @DisplayName("should not delete archived items when they are missing from synced list")
        void shouldNotDeleteArchivedItemsWhenMissingFromSyncedList() {
            // Arrange — the repository query has AND archived = false, so archived items are
            // excluded from deletion. We verify the correct repository method is called, which
            // by its JPQL definition only targets non-archived items.
            List<String> syncedNodeIds = List.of("PVTI_1");
            List<ProjectItem.ContentType> expectedTypes = List.of(
                ProjectItem.ContentType.ISSUE,
                ProjectItem.ContentType.PULL_REQUEST
            );
            // Repository returns 0 — the "missing" items were archived and excluded by the query
            when(
                projectItemRepository.deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                    eq(PROJECT_ID),
                    eq(expectedTypes),
                    eq(syncedNodeIds)
                )
            ).thenReturn(0);

            // Act
            int result = processor.removeStaleIssuePrItems(PROJECT_ID, syncedNodeIds, context);

            // Assert
            assertThat(result).isZero();
            verify(projectItemRepository).deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                eq(PROJECT_ID),
                eq(expectedTypes),
                eq(syncedNodeIds)
            );
        }

        @Test
        @DisplayName("should call deleteByContentTypeIn when synced list is null")
        void shouldCallDeleteByContentTypeInWhenSyncedListIsNull() {
            // Arrange
            List<ProjectItem.ContentType> expectedTypes = List.of(
                ProjectItem.ContentType.ISSUE,
                ProjectItem.ContentType.PULL_REQUEST
            );
            when(projectItemRepository.deleteByProjectIdAndContentTypeIn(eq(PROJECT_ID), eq(expectedTypes))).thenReturn(
                5
            );

            // Act
            int result = processor.removeStaleIssuePrItems(PROJECT_ID, null, context);

            // Assert
            assertThat(result).isEqualTo(5);
            verify(projectItemRepository).deleteByProjectIdAndContentTypeIn(eq(PROJECT_ID), eq(expectedTypes));
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeInAndNodeIdNotIn(any(), any(), any());
        }

        @Test
        @DisplayName("should call deleteByContentTypeIn when synced list is empty")
        void shouldCallDeleteByContentTypeInWhenSyncedListIsEmpty() {
            // Arrange
            List<ProjectItem.ContentType> expectedTypes = List.of(
                ProjectItem.ContentType.ISSUE,
                ProjectItem.ContentType.PULL_REQUEST
            );
            when(projectItemRepository.deleteByProjectIdAndContentTypeIn(eq(PROJECT_ID), eq(expectedTypes))).thenReturn(
                2
            );

            // Act
            int result = processor.removeStaleIssuePrItems(PROJECT_ID, List.of(), context);

            // Assert
            assertThat(result).isEqualTo(2);
            verify(projectItemRepository).deleteByProjectIdAndContentTypeIn(eq(PROJECT_ID), eq(expectedTypes));
            verify(projectItemRepository, never()).deleteByProjectIdAndContentTypeInAndNodeIdNotIn(any(), any(), any());
        }

        @Test
        @DisplayName("should return 0 when no stale items exist")
        void shouldReturnZeroWhenNoStaleItemsExist() {
            // Arrange — all items in the DB are in the synced list
            List<String> syncedNodeIds = List.of("PVTI_1", "PVTI_2", "PVTI_3");
            List<ProjectItem.ContentType> expectedTypes = List.of(
                ProjectItem.ContentType.ISSUE,
                ProjectItem.ContentType.PULL_REQUEST
            );
            when(
                projectItemRepository.deleteByProjectIdAndContentTypeInAndNodeIdNotIn(
                    eq(PROJECT_ID),
                    eq(expectedTypes),
                    eq(syncedNodeIds)
                )
            ).thenReturn(0);

            // Act
            int result = processor.removeStaleIssuePrItems(PROJECT_ID, syncedNodeIds, context);

            // Assert
            assertThat(result).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // processArchived() tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processArchived")
    class ProcessArchived {

        @Test
        @DisplayName("should force archived=true and publish ProjectItemArchived event")
        void shouldForceArchivedTrueAndPublishArchivedEvent() {
            // Arrange — DTO has archived=false (webhook deserialization default)
            GitHubProjectItemDTO dto = createDraftIssueDTO(); // archived=false
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setArchived(true);
            stubSuccessfulProcess(false, entity);

            // Act
            ProjectItem result = processor.processArchived(dto, project, context);

            // Assert
            assertThat(result).isNotNull();

            // Verify that archived=true was forced via withArchived(true)
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(),
                isNull(),
                eq("Draft Title"),
                eq("Draft Body"),
                eq(true), // archived forced to true
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );

            // Verify both the base event (Updated) and the Archived event are published
            // process() publishes Updated (since !isNew), then processArchived publishes Archived
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

            List<Object> events = eventCaptor.getAllValues();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(DomainEvent.ProjectItemUpdated.class);
            assertThat(events.get(1)).isInstanceOf(DomainEvent.ProjectItemArchived.class);

            DomainEvent.ProjectItemArchived archivedEvent = (DomainEvent.ProjectItemArchived) events.get(1);
            assertThat(archivedEvent.projectId()).isEqualTo(PROJECT_ID);
            assertThat(archivedEvent.item().id()).isEqualTo(ITEM_DB_ID);
        }

        @Test
        @DisplayName("should not publish archived event when process returns null")
        void shouldNotPublishArchivedEventWhenProcessReturnsNull() {
            // Arrange — null DTO causes process() to return null
            ProjectItem result = processor.processArchived(null, project, context);

            // Assert
            assertThat(result).isNull();
            verify(eventPublisher, never()).publishEvent(any(DomainEvent.ProjectItemArchived.class));
        }

        @Test
        @DisplayName("should pass actorId to process and archived event")
        void shouldPassActorIdToProcessAndArchivedEvent() {
            // Arrange
            Long actorId = 400L;
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setArchived(true);
            stubSuccessfulProcess(false, entity);

            // Act
            ProjectItem result = processor.processArchived(dto, project, context, actorId);

            // Assert
            assertThat(result).isNotNull();
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

            DomainEvent.ProjectItemArchived archivedEvent = (DomainEvent.ProjectItemArchived) eventCaptor
                .getAllValues()
                .get(1);
            assertThat(archivedEvent.item().actorId()).isEqualTo(actorId);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // processRestored() tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processRestored")
    class ProcessRestored {

        @Test
        @DisplayName("should force archived=false and publish ProjectItemRestored event")
        void shouldForceArchivedFalseAndPublishRestoredEvent() {
            // Arrange — DTO has archived=true (from existing state)
            GitHubProjectItemDTO dto = new GitHubProjectItemDTO(
                null,
                ITEM_DB_ID,
                NODE_ID,
                null,
                "DRAFT_ISSUE",
                null,
                null,
                "Title",
                null,
                true,
                null,
                Collections.emptyList(),
                false,
                0,
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z")
            );
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            entity.setArchived(false);
            stubSuccessfulProcess(false, entity);

            // Act
            ProjectItem result = processor.processRestored(dto, project, context);

            // Assert
            assertThat(result).isNotNull();

            // Verify that archived=false was forced via withArchived(false)
            verify(projectItemRepository).upsertCore(
                eq(ITEM_DB_ID),
                eq(NODE_ID),
                eq(PROJECT_ID),
                eq("DRAFT_ISSUE"),
                isNull(),
                isNull(),
                eq("Title"),
                isNull(),
                eq(false), // archived forced to false
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );

            // Verify both base event (Updated) and Restored event are published
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

            List<Object> events = eventCaptor.getAllValues();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(DomainEvent.ProjectItemUpdated.class);
            assertThat(events.get(1)).isInstanceOf(DomainEvent.ProjectItemRestored.class);

            DomainEvent.ProjectItemRestored restoredEvent = (DomainEvent.ProjectItemRestored) events.get(1);
            assertThat(restoredEvent.projectId()).isEqualTo(PROJECT_ID);
            assertThat(restoredEvent.item().id()).isEqualTo(ITEM_DB_ID);
        }

        @Test
        @DisplayName("should not publish restored event when process returns null")
        void shouldNotPublishRestoredEventWhenProcessReturnsNull() {
            // Arrange — null DTO causes process() to return null
            ProjectItem result = processor.processRestored(null, project, context);

            // Assert
            assertThat(result).isNull();
            verify(eventPublisher, never()).publishEvent(any(DomainEvent.ProjectItemRestored.class));
        }

        @Test
        @DisplayName("should pass actorId to process and restored event")
        void shouldPassActorIdToProcessAndRestoredEvent() {
            // Arrange
            Long actorId = 500L;
            GitHubProjectItemDTO dto = createDraftIssueDTO();
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(false, entity);

            // Act
            ProjectItem result = processor.processRestored(dto, project, context, actorId);

            // Assert
            assertThat(result).isNotNull();
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

            DomainEvent.ProjectItemRestored restoredEvent = (DomainEvent.ProjectItemRestored) eventCaptor
                .getAllValues()
                .get(1);
            assertThat(restoredEvent.item().actorId()).isEqualTo(actorId);
        }

        @Test
        @DisplayName("should be idempotent when dto is already not archived")
        void shouldBeIdempotentWhenDtoAlreadyNotArchived() {
            // Arrange — dto.archived is already false; withArchived(false) returns same instance
            GitHubProjectItemDTO dto = createDraftIssueDTO(); // archived=false
            ProjectItem entity = createProjectItemEntity(ProjectItem.ContentType.DRAFT_ISSUE);
            stubSuccessfulProcess(false, entity);

            // Act
            ProjectItem result = processor.processRestored(dto, project, context);

            // Assert
            assertThat(result).isNotNull();
            // Verify archived=false is passed
            verify(projectItemRepository).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(false),
                any(),
                any(),
                any()
            );
        }
    }
}

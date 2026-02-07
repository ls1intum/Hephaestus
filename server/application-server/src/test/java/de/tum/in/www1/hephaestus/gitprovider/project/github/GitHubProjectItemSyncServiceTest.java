package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO.EmbeddedProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO.EmbeddedProjectReference;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for {@link GitHubProjectItemSyncService}.
 * <p>
 * Focuses on the embedded sync path where project items are processed inline
 * with issue/PR queries, including field value delegation to
 * {@link GitHubProjectItemFieldValueSyncService}.
 */
@DisplayName("GitHubProjectItemSyncService")
class GitHubProjectItemSyncServiceTest extends BaseUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GitHubProjectItemProcessor projectItemProcessor;

    @Mock
    private GitHubProjectItemFieldValueSyncService fieldValueSyncService;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubSyncProperties syncProperties;

    @Mock
    private GitHubExceptionClassifier exceptionClassifier;

    @Mock
    private TransactionTemplate transactionTemplate;

    private GitHubProjectItemSyncService service;

    private static final Long SCOPE_ID = 100L;
    private static final Long PARENT_ISSUE_ID = 500L;
    private static final String PROJECT_NODE_ID = "PVT_node1";
    private static final Long PROJECT_DB_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new GitHubProjectItemSyncService(
            projectRepository,
            projectItemProcessor,
            fieldValueSyncService,
            graphQlClientProvider,
            syncProperties,
            exceptionClassifier,
            transactionTemplate
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private Project createProject() {
        Project project = new Project();
        project.setId(PROJECT_DB_ID);
        project.setNodeId(PROJECT_NODE_ID);
        project.setNumber(1);
        project.setTitle("Test Project");
        return project;
    }

    private ProjectItem createProjectItem(Long id) {
        ProjectItem item = new ProjectItem();
        item.setId(id);
        item.setNodeId("PVTI_item" + id);
        return item;
    }

    private GitHubProjectItemDTO createItemDTO(String nodeId, List<GitHubProjectFieldValueDTO> fieldValues) {
        return new GitHubProjectItemDTO(
            null,
            100L,
            nodeId,
            PROJECT_NODE_ID,
            "ISSUE",
            null,
            42,
            null,
            null,
            false,
            null,
            fieldValues,
            false,
            fieldValues != null ? fieldValues.size() : 0,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    private GitHubProjectFieldValueDTO createFieldValueDTO(String fieldId, String type, String value) {
        return new GitHubProjectFieldValueDTO(fieldId, type, value, null, null, null, null);
    }

    private EmbeddedProjectReference createProjectRef() {
        return new EmbeddedProjectReference(
            PROJECT_NODE_ID,
            PROJECT_DB_ID,
            1,
            "Test Project",
            "https://github.com/orgs/test/projects/1",
            "test-org",
            42L,
            "ORGANIZATION"
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // processEmbeddedItems tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processEmbeddedItems")
    class ProcessEmbeddedItems {

        @Test
        @DisplayName("should return 0 when embeddedItems is null")
        void shouldReturnZeroWhenEmbeddedItemsIsNull() {
            // Act
            int result = service.processEmbeddedItems(null, ProcessingContext.forSync(SCOPE_ID, null), PARENT_ISSUE_ID);

            // Assert
            assertThat(result).isZero();
            verify(projectItemProcessor, never()).process(any(), any(), any());
        }

        @Test
        @DisplayName("should return 0 when items list is empty")
        void shouldReturnZeroWhenItemsListIsEmpty() {
            // Arrange
            EmbeddedProjectItemsDTO emptyItems = EmbeddedProjectItemsDTO.empty();

            // Act
            int result = service.processEmbeddedItems(
                emptyItems,
                ProcessingContext.forSync(SCOPE_ID, null),
                PARENT_ISSUE_ID
            );

            // Assert
            assertThat(result).isZero();
            verify(projectItemProcessor, never()).process(any(), any(), any());
        }

        @Test
        @DisplayName("should process items and delegate field values to fieldValueSyncService")
        void shouldProcessItemsAndDelegateFieldValues() {
            // Arrange
            Project project = createProject();
            when(projectRepository.findByNodeId(PROJECT_NODE_ID)).thenReturn(Optional.of(project));

            GitHubProjectFieldValueDTO fv1 = createFieldValueDTO("field-1", "TEXT", "value1");
            GitHubProjectFieldValueDTO fv2 = createFieldValueDTO("field-2", "NUMBER", null);
            GitHubProjectItemDTO itemDto = createItemDTO("PVTI_1", List.of(fv1, fv2));

            EmbeddedProjectItem embeddedItem = new EmbeddedProjectItem(itemDto, createProjectRef());
            EmbeddedProjectItemsDTO embeddedItems = new EmbeddedProjectItemsDTO(List.of(embeddedItem), 1, false, null);

            ProjectItem processedItem = createProjectItem(77L);
            when(projectItemProcessor.process(any(GitHubProjectItemDTO.class), eq(project), any())).thenReturn(
                processedItem
            );

            // Act
            int result = service.processEmbeddedItems(
                embeddedItems,
                ProcessingContext.forSync(SCOPE_ID, null),
                PARENT_ISSUE_ID
            );

            // Assert
            assertThat(result).isEqualTo(1);
            verify(fieldValueSyncService).processFieldValues(eq(77L), any(), eq(false), eq(null));
        }

        @Test
        @DisplayName("should pass truncation info to fieldValueSyncService")
        void shouldPassTruncationInfoToFieldValueSyncService() {
            // Arrange
            Project project = createProject();
            when(projectRepository.findByNodeId(PROJECT_NODE_ID)).thenReturn(Optional.of(project));

            // Create a truncated item — more field values exist than were fetched inline
            GitHubProjectFieldValueDTO fv1 = createFieldValueDTO("field-1", "TEXT", "value1");
            GitHubProjectItemDTO itemDto = new GitHubProjectItemDTO(
                null,
                100L,
                "PVTI_trunc",
                PROJECT_NODE_ID,
                "ISSUE",
                null,
                42,
                null,
                null,
                false,
                null,
                List.of(fv1),
                true, // truncated
                15, // totalCount > fetched
                "cursor-abc", // endCursor for pagination
                Instant.now(),
                Instant.now()
            );

            EmbeddedProjectItem embeddedItem = new EmbeddedProjectItem(itemDto, createProjectRef());
            EmbeddedProjectItemsDTO embeddedItems = new EmbeddedProjectItemsDTO(List.of(embeddedItem), 1, false, null);

            ProjectItem processedItem = createProjectItem(88L);
            when(projectItemProcessor.process(any(GitHubProjectItemDTO.class), eq(project), any())).thenReturn(
                processedItem
            );

            // Act
            service.processEmbeddedItems(embeddedItems, ProcessingContext.forSync(SCOPE_ID, null), PARENT_ISSUE_ID);

            // Assert — truncation flag and cursor are forwarded
            verify(fieldValueSyncService).processFieldValues(eq(88L), any(), eq(true), eq("cursor-abc"));
        }

        @Test
        @DisplayName("should skip items when project is not synced yet")
        void shouldSkipItemsWhenProjectNotSynced() {
            // Arrange — project not found in repository
            when(projectRepository.findByNodeId(PROJECT_NODE_ID)).thenReturn(Optional.empty());

            GitHubProjectItemDTO itemDto = createItemDTO("PVTI_orphan", List.of());
            EmbeddedProjectItem embeddedItem = new EmbeddedProjectItem(itemDto, createProjectRef());
            EmbeddedProjectItemsDTO embeddedItems = new EmbeddedProjectItemsDTO(List.of(embeddedItem), 1, false, null);

            // Act
            int result = service.processEmbeddedItems(
                embeddedItems,
                ProcessingContext.forSync(SCOPE_ID, null),
                PARENT_ISSUE_ID
            );

            // Assert
            assertThat(result).isZero();
            verify(projectItemProcessor, never()).process(any(), any(), any());
            verify(fieldValueSyncService, never()).processFieldValues(any(), any(), any(boolean.class), any());
        }

        @Test
        @DisplayName("should continue processing remaining items when one fails")
        void shouldContinueProcessingWhenOneFails() {
            // Arrange
            Project project = createProject();
            when(projectRepository.findByNodeId(PROJECT_NODE_ID)).thenReturn(Optional.of(project));

            GitHubProjectItemDTO item1 = createItemDTO("PVTI_fail", List.of());
            GitHubProjectItemDTO item2 = createItemDTO("PVTI_ok", List.of());

            EmbeddedProjectItem embedded1 = new EmbeddedProjectItem(item1, createProjectRef());
            EmbeddedProjectItem embedded2 = new EmbeddedProjectItem(item2, createProjectRef());
            EmbeddedProjectItemsDTO embeddedItems = new EmbeddedProjectItemsDTO(
                List.of(embedded1, embedded2),
                2,
                false,
                null
            );

            // First item throws, second succeeds
            when(projectItemProcessor.process(any(GitHubProjectItemDTO.class), eq(project), any()))
                .thenThrow(new RuntimeException("DB error"))
                .thenReturn(createProjectItem(99L));

            // Act
            int result = service.processEmbeddedItems(
                embeddedItems,
                ProcessingContext.forSync(SCOPE_ID, null),
                PARENT_ISSUE_ID
            );

            // Assert — one succeeded despite the first failing
            assertThat(result).isEqualTo(1);
            verify(fieldValueSyncService).processFieldValues(eq(99L), any(), eq(false), eq(null));
        }

        @Test
        @DisplayName("should not call fieldValueSyncService when processor returns null")
        void shouldNotCallFieldValueSyncServiceWhenProcessorReturnsNull() {
            // Arrange
            Project project = createProject();
            when(projectRepository.findByNodeId(PROJECT_NODE_ID)).thenReturn(Optional.of(project));

            GitHubProjectItemDTO itemDto = createItemDTO("PVTI_null", List.of());
            EmbeddedProjectItem embeddedItem = new EmbeddedProjectItem(itemDto, createProjectRef());
            EmbeddedProjectItemsDTO embeddedItems = new EmbeddedProjectItemsDTO(List.of(embeddedItem), 1, false, null);

            when(projectItemProcessor.process(any(GitHubProjectItemDTO.class), eq(project), any())).thenReturn(null);

            // Act
            int result = service.processEmbeddedItems(
                embeddedItems,
                ProcessingContext.forSync(SCOPE_ID, null),
                PARENT_ISSUE_ID
            );

            // Assert
            assertThat(result).isZero();
            verify(fieldValueSyncService, never()).processFieldValues(any(), any(), any(boolean.class), any());
        }
    }
}

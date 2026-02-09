package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link GitHubProjectItemProcessor}.
 * <p>
 * Focuses on stale item removal logic, particularly the behavior around
 * archived items which must be preserved during project-side sync because
 * GitHub's {@code ProjectV2.items} connection excludes them.
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
    }

    // ═══════════════════════════════════════════════════════════════
    // removeStaleIssuePrItems tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeStaleIssuePrItems")
    class RemoveStaleIssuePrItems {

        private final ProcessingContext context = ProcessingContext.forSync(SCOPE_ID, null);

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
}

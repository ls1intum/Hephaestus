package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabProjectResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("GitLabProjectProcessor")
class GitLabProjectProcessorTest extends BaseUnitTest {

    @Mock
    private RepositoryRepository repositoryRepository;

    private GitLabProjectProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GitLabProjectProcessor(repositoryRepository);
    }

    @Nested
    @DisplayName("processGraphQlResponse")
    class ProcessGraphQlResponse {

        @BeforeEach
        void stubSave() {
            // Lenient: null/invalid input tests return early before reaching save()
            lenient()
                .when(repositoryRepository.save(any(Repository.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("valid project maps all fields correctly")
        void validProject_mapsAllFields() {
            var repoInfo = new GitLabProjectResponse.RepositoryInfo("develop");
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "my-org/my-project",
                "my-project",
                "https://gitlab.com/my-org/my-project",
                "A cool project",
                "public",
                false,
                "2024-01-15T10:30:00Z",
                "2024-06-20T14:00:00Z",
                null,
                repoInfo
            );

            Organization org = new Organization();
            org.setId(42L);

            when(repositoryRepository.findById(-123L)).thenReturn(Optional.empty());

            Repository result = processor.processGraphQlResponse(project, org);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(-123L);
            assertThat(result.getName()).isEqualTo("my-project");
            assertThat(result.getNameWithOwner()).isEqualTo("my-org/my-project");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/my-org/my-project");
            assertThat(result.getDescription()).isEqualTo("A cool project");
            assertThat(result.getVisibility()).isEqualTo(Repository.Visibility.PUBLIC);
            assertThat(result.isPrivate()).isFalse();
            assertThat(result.isArchived()).isFalse();
            assertThat(result.isDisabled()).isFalse();
            assertThat(result.isHasDiscussionsEnabled()).isFalse();
            assertThat(result.getDefaultBranch()).isEqualTo("develop");
            assertThat(result.getOrganization()).isEqualTo(org);
            assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
            assertThat(result.getPushedAt()).isEqualTo(Instant.parse("2024-06-20T14:00:00Z"));
            assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2024-06-20T14:00:00Z"));
            assertThat(result.getLastSyncAt()).isNotNull();
        }

        @Test
        @DisplayName("private visibility sets isPrivate true")
        void privateVisibility_setsIsPrivateTrue() {
            var project = createMinimalProject("private");
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.empty());

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result.getVisibility()).isEqualTo(Repository.Visibility.PRIVATE);
            assertThat(result.isPrivate()).isTrue();
        }

        @Test
        @DisplayName("internal visibility maps correctly")
        void internalVisibility_mapsCorrectly() {
            var project = createMinimalProject("internal");
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.empty());

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result.getVisibility()).isEqualTo(Repository.Visibility.INTERNAL);
            assertThat(result.isPrivate()).isFalse();
        }

        @Test
        @DisplayName("null repository rootRef falls back to main")
        void nullRootRef_fallsBackToMain() {
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "org/project",
                "project",
                "https://gitlab.com/org/project",
                null,
                "public",
                false,
                null,
                null,
                null,
                null // no repository info
            );
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.empty());

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result.getDefaultBranch()).isEqualTo("main");
        }

        @Test
        @DisplayName("archived project sets isArchived true")
        void archivedProject_setsFlag() {
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "org/project",
                "project",
                "https://gitlab.com/org/project",
                null,
                "public",
                true, // archived
                null,
                null,
                null,
                null
            );
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.empty());

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result.isArchived()).isTrue();
        }

        @Test
        @DisplayName("existing entity is updated, not duplicated")
        void existingEntity_isUpdated() {
            var project = createMinimalProject("public");
            Repository existing = new Repository();
            existing.setId(-123L);
            existing.setName("old-name");
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.of(existing));

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result.getName()).isEqualTo("project");
            // Verify save was called (not insert)
            ArgumentCaptor<Repository> captor = ArgumentCaptor.forClass(Repository.class);
            verify(repositoryRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(-123L);
        }

        @Test
        @DisplayName("null response returns null")
        void nullResponse_returnsNull() {
            assertThat(processor.processGraphQlResponse(null, null)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("null webUrl returns null")
        void nullWebUrl_returnsNull() {
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "org/project",
                "project",
                null, // null webUrl
                null,
                "public",
                false,
                null,
                null,
                null,
                null
            );
            assertThat(processor.processGraphQlResponse(project, null)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("malformed createdAt does not overwrite existing value")
        void malformedCreatedAt_doesNotOverwriteExisting() {
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "org/project",
                "project",
                "https://gitlab.com/org/project",
                null,
                "public",
                false,
                "not-a-date", // malformed
                "2024-06-01T10:00:00Z",
                null,
                new GitLabProjectResponse.RepositoryInfo("main")
            );

            Instant existingCreatedAt = Instant.parse("2024-01-01T00:00:00Z");
            Repository existing = new Repository();
            existing.setId(-123L);
            existing.setCreatedAt(existingCreatedAt);
            when(repositoryRepository.findById(-123L)).thenReturn(Optional.of(existing));

            Repository result = processor.processGraphQlResponse(project, null);

            assertThat(result).isNotNull();
            assertThat(result.getCreatedAt()).isEqualTo(existingCreatedAt);
        }

        @Test
        @DisplayName("null fullPath returns null")
        void nullFullPath_returnsNull() {
            var project = new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                null,
                "project",
                "https://gitlab.com/org/project",
                null,
                "public",
                false,
                null,
                null,
                null,
                null
            );
            assertThat(processor.processGraphQlResponse(project, null)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("invalid GID returns null")
        void invalidGid_returnsNull() {
            var project = new GitLabProjectResponse(
                "not-a-valid-gid",
                "org/project",
                "project",
                "https://gitlab.com/org/project",
                null,
                "public",
                false,
                null,
                null,
                null,
                null
            );
            assertThat(processor.processGraphQlResponse(project, null)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        private GitLabProjectResponse createMinimalProject(String visibility) {
            return new GitLabProjectResponse(
                "gid://gitlab/Project/123",
                "org/project",
                "project",
                "https://gitlab.com/org/project",
                null,
                visibility,
                false,
                null,
                null,
                null,
                new GitLabProjectResponse.RepositoryInfo("main")
            );
        }
    }

    @Nested
    @DisplayName("processPushEvent")
    class ProcessPushEvent {

        @BeforeEach
        void stubSave() {
            // Lenient: null/invalid input tests return early before reaching save()
            lenient()
                .when(repositoryRepository.save(any(Repository.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("valid push event project info creates repository")
        void validPushEvent_createsRepository() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(
                246765L,
                "demo-repository",
                "Demo repo",
                "https://gitlab.lrz.de/hephaestustest/demo-repository",
                "HephaestusTest",
                "hephaestustest/demo-repository",
                "main",
                0 // private
            );

            when(repositoryRepository.findById(-246765L)).thenReturn(Optional.empty());

            Repository result = processor.processPushEvent(projectInfo);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(-246765L);
            assertThat(result.getName()).isEqualTo("demo-repository");
            assertThat(result.getNameWithOwner()).isEqualTo("hephaestustest/demo-repository");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.lrz.de/hephaestustest/demo-repository");
            assertThat(result.getVisibility()).isEqualTo(Repository.Visibility.PRIVATE);
            assertThat(result.isPrivate()).isTrue();
            assertThat(result.getDefaultBranch()).isEqualTo("main");
            assertThat(result.getPushedAt()).isNotNull();
        }

        @Test
        @DisplayName("public visibility level maps correctly")
        void publicVisibilityLevel_mapsCorrectly() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(
                1L,
                "proj",
                null,
                "https://gitlab.com/org/proj",
                null,
                "org/proj",
                "main",
                20
            );
            when(repositoryRepository.findById(-1L)).thenReturn(Optional.empty());

            Repository result = processor.processPushEvent(projectInfo);

            assertThat(result.getVisibility()).isEqualTo(Repository.Visibility.PUBLIC);
            assertThat(result.isPrivate()).isFalse();
        }

        @Test
        @DisplayName("null project info returns null")
        void nullProjectInfo_returnsNull() {
            assertThat(processor.processPushEvent(null)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("null webUrl in project info returns null")
        void nullWebUrl_returnsNull() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(1L, "proj", null, null, null, "org/proj", "main", 0);
            assertThat(processor.processPushEvent(projectInfo)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("null id in project info returns null")
        void nullId_returnsNull() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(
                null,
                "proj",
                null,
                "https://gitlab.com/org/proj",
                null,
                "org/proj",
                "main",
                0
            );
            assertThat(processor.processPushEvent(projectInfo)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("null pathWithNamespace in project info returns null")
        void nullPathWithNamespace_returnsNull() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(
                1L,
                "proj",
                null,
                "https://gitlab.com/org/proj",
                null,
                null,
                "main",
                0
            );
            assertThat(processor.processPushEvent(projectInfo)).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("existing repository is updated on push")
        void existingRepository_isUpdated() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(
                1L,
                "new-name",
                null,
                "https://gitlab.com/org/new-name",
                null,
                "org/new-name",
                "develop",
                20
            );
            Repository existing = new Repository();
            existing.setId(-1L);
            existing.setName("old-name");
            existing.setArchived(true); // should be preserved
            when(repositoryRepository.findById(-1L)).thenReturn(Optional.of(existing));

            Repository result = processor.processPushEvent(projectInfo);

            assertThat(result.getName()).isEqualTo("new-name");
            assertThat(result.getDefaultBranch()).isEqualTo("develop");
            assertThat(result.isArchived()).isTrue(); // preserved from existing entity
        }
    }

    @Nested
    @DisplayName("mapVisibility")
    class MapVisibility {

        @Test
        @DisplayName("all visibility strings map correctly")
        void allVisibilityStrings() {
            assertThat(GitLabProjectProcessor.mapVisibility("public")).isEqualTo(Repository.Visibility.PUBLIC);
            assertThat(GitLabProjectProcessor.mapVisibility("private")).isEqualTo(Repository.Visibility.PRIVATE);
            assertThat(GitLabProjectProcessor.mapVisibility("internal")).isEqualTo(Repository.Visibility.INTERNAL);
            assertThat(GitLabProjectProcessor.mapVisibility("PUBLIC")).isEqualTo(Repository.Visibility.PUBLIC);
            assertThat(GitLabProjectProcessor.mapVisibility(null)).isEqualTo(Repository.Visibility.UNKNOWN);
            assertThat(GitLabProjectProcessor.mapVisibility("unknown-value")).isEqualTo(Repository.Visibility.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("mapVisibilityLevel")
    class MapVisibilityLevel {

        @Test
        @DisplayName("all visibility levels map correctly")
        void allVisibilityLevels() {
            assertThat(GitLabProjectProcessor.mapVisibilityLevel(0)).isEqualTo(Repository.Visibility.PRIVATE);
            assertThat(GitLabProjectProcessor.mapVisibilityLevel(10)).isEqualTo(Repository.Visibility.INTERNAL);
            assertThat(GitLabProjectProcessor.mapVisibilityLevel(20)).isEqualTo(Repository.Visibility.PUBLIC);
            assertThat(GitLabProjectProcessor.mapVisibilityLevel(99)).isEqualTo(Repository.Visibility.UNKNOWN);
        }
    }
}

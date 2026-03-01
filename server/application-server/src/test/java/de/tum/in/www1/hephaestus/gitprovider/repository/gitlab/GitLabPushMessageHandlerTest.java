package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("GitLabPushMessageHandler")
class GitLabPushMessageHandlerTest extends BaseUnitTest {

    private static final Long PROVIDER_ID = 2L;
    private static final String DEFAULT_SERVER_URL = "https://gitlab.lrz.de";

    @Mock
    private GitLabProjectProcessor projectProcessor;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    @Mock
    private NatsMessageDeserializer deserializer;

    private TransactionTemplate transactionTemplate;
    private GitLabPushMessageHandler handler;
    private GitProvider gitLabProvider;

    @BeforeEach
    void setUp() {
        transactionTemplate = mock(TransactionTemplate.class);
        // Lenient: not all tests trigger transactional execution (e.g., getEventType, nonPushSubject)
        lenient()
            .doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<TransactionStatus> callback = invocation.getArgument(0);
                callback.accept(null);
                return null;
            })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        GitLabProperties properties = new GitLabProperties(
            DEFAULT_SERVER_URL,
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        gitLabProvider = new GitProvider();
        gitLabProvider.setId(PROVIDER_ID);
        gitLabProvider.setType(GitProviderType.GITLAB);
        gitLabProvider.setServerUrl(DEFAULT_SERVER_URL);

        // Default: provider lookup succeeds
        lenient()
            .when(gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITLAB, DEFAULT_SERVER_URL))
            .thenReturn(Optional.of(gitLabProvider));

        handler = new GitLabPushMessageHandler(
            projectProcessor,
            organizationRepository,
            repositoryRepository,
            gitProviderRepository,
            properties,
            deserializer,
            transactionTemplate
        );
    }

    @Test
    @DisplayName("returns PUSH event type")
    void getEventType_returnsPush() {
        assertThat(handler.getEventType()).isEqualTo(GitLabEventType.PUSH);
    }

    @Test
    @DisplayName("valid push event upserts project")
    void validPushEvent_upsertsProject() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            246765L,
            "demo-repository",
            "Demo repo",
            "https://gitlab.lrz.de/hephaestustest/demo-repository",
            "HephaestusTest",
            "hephaestustest/demo-repository",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "9c5dedd52046bb5213189afc25f75e608a98d462",
            "a4bf10d93a2d136f1db911b6f1c03d26d835a44f",
            "a4bf10d93a2d136f1db911b6f1c03d26d835a44f",
            246765L,
            projectInfo,
            3
        );

        Repository repo = new Repository();
        repo.setId(246765L);
        when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);

        // Org lookup — simulate existing org in DB
        Organization org = new Organization();
        org.setId(1L);
        org.setLogin("hephaestustest");
        when(organizationRepository.findByLoginIgnoreCase("hephaestustest")).thenReturn(Optional.of(org));

        Message msg = mockMessage("gitlab.hephaestustest.demo-repository.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor).processPushEvent(projectInfo, gitLabProvider);
        verify(repositoryRepository).save(repo);
        assertThat(repo.getOrganization()).isSameAs(org);
    }

    @Test
    @DisplayName("branch deletion skips processing")
    void branchDeletion_skipsProcessing() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            1L,
            "proj",
            null,
            "https://gitlab.com/org/proj",
            null,
            "org/proj",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/feature-branch",
            "abc123",
            "0000000000000000000000000000000000000000", // branch deletion
            null,
            1L,
            projectInfo,
            0
        );

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor, never()).processPushEvent(any(), any());
    }

    @Test
    @DisplayName("push event with null project skips processing")
    void nullProject_skipsProcessing() throws IOException {
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "before",
            "after",
            "after",
            null,
            null, // null project
            0
        );

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor, never()).processPushEvent(any(), any());
    }

    @Test
    @DisplayName("processor returning null logs warning")
    void processorReturnsNull_logsWarning() throws IOException {
        var projectInfo = new GitLabPushEventDTO.ProjectInfo(
            1L,
            "proj",
            null,
            "https://gitlab.com/org/proj",
            null,
            "org/proj",
            "main",
            0
        );
        var pushEvent = new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "before",
            "after",
            "after",
            1L,
            projectInfo,
            1
        );

        when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(null);

        Message msg = mockMessage("gitlab.org.proj.push", pushEvent);
        handler.onMessage(msg);

        verify(projectProcessor).processPushEvent(projectInfo, gitLabProvider);
        // Processor returned null — no org linking attempted
        verify(organizationRepository, never()).findByLoginIgnoreCase(anyString());
    }

    @Test
    @DisplayName("non-push subject is rejected by base class")
    void nonPushSubject_rejected() throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("gitlab.org.proj.merge_request");

        handler.onMessage(msg);

        verify(deserializer, never()).deserialize(any(), any());
        verify(projectProcessor, never()).processPushEvent(any(), any());
    }

    // ========================================================================
    // Organization Linking Tests
    // ========================================================================

    @Nested
    @DisplayName("Organization linking")
    class OrganizationLinking {

        @Test
        @DisplayName("skips linking when org already set")
        void skipsWhenOrgAlreadySet() throws IOException {
            Organization existingOrg = new Organization();
            existingOrg.setId(1L);

            Repository repo = new Repository();
            repo.setId(1L);
            repo.setOrganization(existingOrg); // already linked

            var projectInfo = createProjectInfo(1L, "org/proj");
            when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);

            Message msg = mockMessage("gitlab.org.proj.push", createPushEvent(projectInfo));
            handler.onMessage(msg);

            // Should NOT look up org since it's already linked
            verify(organizationRepository, never()).findByLoginIgnoreCase(anyString());
        }

        @Test
        @DisplayName("links org from DB lookup")
        void linksOrgFromDbLookup() throws IOException {
            Repository repo = new Repository();
            repo.setId(1L);

            var projectInfo = createProjectInfo(1L, "org/proj");
            when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);

            Organization org = new Organization();
            org.setId(42L);
            when(organizationRepository.findByLoginIgnoreCase("org")).thenReturn(Optional.of(org));

            Message msg = mockMessage("gitlab.org.proj.push", createPushEvent(projectInfo));
            handler.onMessage(msg);

            assertThat(repo.getOrganization()).isSameAs(org);
            verify(repositoryRepository).save(repo);
        }

        @Test
        @DisplayName("skips linking when org not yet in DB (deferred to full sync)")
        void skipsLinkingWhenOrgNotInDb() throws IOException {
            Repository repo = new Repository();
            repo.setId(1L);

            var projectInfo = createProjectInfo(1L, "org/proj");
            when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);
            when(organizationRepository.findByLoginIgnoreCase("org")).thenReturn(Optional.empty());

            Message msg = mockMessage("gitlab.org.proj.push", createPushEvent(projectInfo));
            handler.onMessage(msg);

            // Org not found in DB — repo stays unlinked, will be resolved on next full sync
            assertThat(repo.getOrganization()).isNull();
            verify(repositoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("handles nested group paths correctly")
        void handlesNestedGroupPaths() throws IOException {
            Repository repo = new Repository();
            repo.setId(1L);

            // Project in nested group: org/team/subteam/project
            var projectInfo = createProjectInfo(1L, "org/team/subteam/project");
            when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);

            Organization org = new Organization();
            org.setId(42L);
            // Should look up "org/team/subteam" (immediate parent)
            when(organizationRepository.findByLoginIgnoreCase("org/team/subteam")).thenReturn(Optional.of(org));

            Message msg = mockMessage("gitlab.org.team.subteam.project.push", createPushEvent(projectInfo));
            handler.onMessage(msg);

            assertThat(repo.getOrganization()).isSameAs(org);
        }

        @Test
        @DisplayName("skips org linking for user-owned project (no group)")
        void skipsForUserOwnedProject() throws IOException {
            Repository repo = new Repository();
            repo.setId(1L);

            // User-owned project has no slash in path
            var projectInfo = createProjectInfo(1L, "myproject");
            when(projectProcessor.processPushEvent(projectInfo, gitLabProvider)).thenReturn(repo);

            Message msg = mockMessage("gitlab.myproject.push", createPushEvent(projectInfo));
            handler.onMessage(msg);

            verify(organizationRepository, never()).findByLoginIgnoreCase(anyString());
        }
    }

    // ========================================================================
    // extractGroupPath Tests
    // ========================================================================

    @Nested
    @DisplayName("extractGroupPath")
    class ExtractGroupPath {

        @Test
        @DisplayName("simple org/project")
        void simpleOrgProject() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("org/project")).isEqualTo("org");
        }

        @Test
        @DisplayName("nested org/team/project")
        void nestedPath() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("org/team/project")).isEqualTo("org/team");
        }

        @Test
        @DisplayName("deeply nested")
        void deeplyNested() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("a/b/c/d/project")).isEqualTo("a/b/c/d");
        }

        @Test
        @DisplayName("no slash returns null")
        void noSlash() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("project")).isNull();
        }

        @Test
        @DisplayName("null returns null")
        void nullInput() {
            assertThat(GitLabPushMessageHandler.extractGroupPath(null)).isNull();
        }

        @Test
        @DisplayName("blank returns null")
        void blankInput() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("  ")).isNull();
        }

        @Test
        @DisplayName("leading slash returns null")
        void leadingSlash() {
            assertThat(GitLabPushMessageHandler.extractGroupPath("/project")).isNull();
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private GitLabPushEventDTO.ProjectInfo createProjectInfo(Long id, String pathWithNamespace) {
        return new GitLabPushEventDTO.ProjectInfo(
            id,
            "proj",
            null,
            "https://gitlab.com/" + pathWithNamespace,
            null,
            pathWithNamespace,
            "main",
            0
        );
    }

    private GitLabPushEventDTO createPushEvent(GitLabPushEventDTO.ProjectInfo projectInfo) {
        return new GitLabPushEventDTO(
            "push",
            "refs/heads/main",
            "before",
            "after",
            "after",
            projectInfo.id(),
            projectInfo,
            1
        );
    }

    private Message mockMessage(String subject, GitLabPushEventDTO event) throws IOException {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(subject);
        when(deserializer.deserialize(msg, GitLabPushEventDTO.class)).thenReturn(event);
        return msg;
    }
}

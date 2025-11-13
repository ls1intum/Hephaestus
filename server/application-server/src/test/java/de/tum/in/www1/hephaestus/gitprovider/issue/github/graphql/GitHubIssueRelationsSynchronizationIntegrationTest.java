package de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLink;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLinkRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLinkType;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.IssueRelationsUpdater;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.GitProviderMode;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kohsuke.github.GHEventPayloadSubIssue;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@Transactional
@Tag("github-integration")
class GitHubIssueRelationsSynchronizationIntegrationTest {

    private static final String APP_ID = System.getenv("GITHUB_APP_ID");
    private static final String APP_PRIVATE_KEY = System.getenv("GITHUB_APP_PRIVATE_KEY");
    private static final Long INSTALLATION_ID = parseLong(System.getenv("GITHUB_TEST_INSTALLATION_ID"));
    private static final String OWNER = System.getenv("GITHUB_TEST_OWNER");
    private static final String REPOSITORY_NAME = System.getenv("GITHUB_TEST_REPOSITORY");
    private static final Integer PARENT_ISSUE_NUMBER = parseInt(System.getenv("GITHUB_TEST_PARENT_ISSUE_NUMBER"));
    private static final Integer SUB_ISSUE_NUMBER = parseInt(System.getenv("GITHUB_TEST_SUB_ISSUE_NUMBER"));
    private static final Integer DEPENDENCY_ISSUE_NUMBER = parseInt(System.getenv("GITHUB_TEST_DEPENDENCY_ISSUE_NUMBER"));

    @DynamicPropertySource
    static void githubAppProperties(DynamicPropertyRegistry registry) {
        registry.add("github.app.id", () -> APP_ID != null ? APP_ID : "0");
        registry.add("github.app.privateKey", () -> APP_PRIVATE_KEY != null ? APP_PRIVATE_KEY : "");
    }

    @Autowired
    private GitHubIssueRelationsGraphQLService relationsService;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubIssueSyncService issueSyncService;

    @Autowired
    private IssueRelationsUpdater relationsUpdater;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssueLinkRepository issueLinkRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private GHRepository repository;

    @BeforeEach
    void setup() {
        assumeTrue(hasText(APP_ID));
        assumeTrue(hasText(APP_PRIVATE_KEY));
        assumeTrue(INSTALLATION_ID != null);
        assumeTrue(hasText(OWNER));
        assumeTrue(hasText(REPOSITORY_NAME));
        assumeTrue(PARENT_ISSUE_NUMBER != null);
        assumeTrue(SUB_ISSUE_NUMBER != null);

        workspace = ensureWorkspace();
        repository = repositorySyncService
            .syncRepository(workspace.getId(), OWNER + "/" + REPOSITORY_NAME)
            .orElseThrow(() -> new IllegalStateException("Repository not accessible"));
    }

    @Test
    void synchronizeGraphQlPersistsRelationships() {
        GHIssue ghIssue = issueSyncService.syncIssue(repository, PARENT_ISSUE_NUMBER).orElseThrow();

        IssueRelationsSnapshot snapshot = relationsService.synchronizeForWorkspace(
            workspace.getId(),
            OWNER,
            REPOSITORY_NAME,
            PARENT_ISSUE_NUMBER,
            IssueRelationsPageRequest.defaults()
        );

        Issue persistedIssue = issueRepository.findById(ghIssue.getId()).orElseThrow();

        assertThat(persistedIssue.getSubIssuesTotal()).isEqualTo(snapshot.subIssuesSummary().total());
        assertThat(persistedIssue.getTrackedIssuesOpen()).isEqualTo(snapshot.trackedIssuesOpen());
        assertThat(persistedIssue.getTrackedIssuesClosed()).isEqualTo(snapshot.trackedIssuesClosed());
        assertThat(persistedIssue.getTrackedIssuesTotal()).isEqualTo(snapshot.trackedIssuesTotal());

        List<IssueLink> subIssueLinks = issueLinkRepository.findBySourceIdAndType(
            persistedIssue.getId(),
            IssueLinkType.SUB_ISSUE
        );
        assertThat(subIssueLinks)
            .extracting(link -> link.getTarget().getNumber())
            .contains(SUB_ISSUE_NUMBER);

        if (DEPENDENCY_ISSUE_NUMBER != null) {
            List<IssueLink> dependencyLinks = issueLinkRepository.findBySourceIdAndType(
                persistedIssue.getId(),
                IssueLinkType.DEPENDS_ON
            );
            assertThat(dependencyLinks)
                .extracting(link -> link.getTarget().getNumber())
                .contains(DEPENDENCY_ISSUE_NUMBER);
        }
    }

    @Test
    void webhookPayloadKeepsRelationsInSync() throws Exception {
        // Ensure baseline state using GraphQL snapshot
        relationsService.synchronizeForWorkspace(
            workspace.getId(),
            OWNER,
            REPOSITORY_NAME,
            PARENT_ISSUE_NUMBER,
            IssueRelationsPageRequest.defaults()
        );

        GHIssue ghIssue = issueSyncService.syncIssue(repository, PARENT_ISSUE_NUMBER).orElseThrow();
        Issue parentIssue = issueRepository.findById(ghIssue.getId()).orElseThrow();

        GHEventPayloadSubIssue removalPayload = loadPayload("github/sub_issues.sub_issue_removed.json");
        relationsUpdater.applyWebhook(workspace.getId(), removalPayload);

        assertThat(issueLinkRepository.findBySourceIdAndType(parentIssue.getId(), IssueLinkType.SUB_ISSUE)).isEmpty();

        GHEventPayloadSubIssue additionPayload = loadPayload("github/sub_issues.sub_issue_added.json");
        relationsUpdater.applyWebhook(workspace.getId(), additionPayload);

        List<IssueLink> links = issueLinkRepository.findBySourceIdAndType(parentIssue.getId(), IssueLinkType.SUB_ISSUE);
        assertThat(links)
            .extracting(link -> link.getTarget().getNumber())
            .contains(SUB_ISSUE_NUMBER);

    Issue refreshedParent = issueRepository.findById(parentIssue.getId()).orElseThrow();
        assertThat(refreshedParent.getSubIssuesTotal())
            .isEqualTo(additionPayload.getParentIssue().getSubIssuesSummary().total());
        assertThat(refreshedParent.getDependenciesBlocking())
            .isEqualTo(additionPayload.getParentIssue().getDependencySummary().blocking());
    }

    private Workspace ensureWorkspace() {
        Workspace ws = workspaceRepository.findByInstallationId(INSTALLATION_ID).orElse(null);
        if (ws != null) {
            return ws;
        }
        Workspace created = new Workspace();
        created.setGitProviderMode(GitProviderMode.GITHUB_APP_INSTALLATION);
        created.setInstallationId(INSTALLATION_ID);
        created.setAccountLogin(OWNER);
        return workspaceRepository.save(created);
    }

    private GHEventPayloadSubIssue loadPayload(String path) throws IOException {
        String payload = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        try (StringReader reader = new StringReader(payload)) {
            return GitHub.offline().parseEventPayload(reader, GHEventPayloadSubIssue.class);
        }
    }

    private static Long parseLong(String value) {
        try {
            return value != null && !value.isBlank() ? Long.parseLong(value) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return value != null && !value.isBlank() ? Integer.parseInt(value) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

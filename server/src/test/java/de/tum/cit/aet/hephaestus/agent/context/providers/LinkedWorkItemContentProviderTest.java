package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class LinkedWorkItemContentProviderTest extends BaseUnitTest {

    private static final long REPO_ID = 123L;
    private static final long PR_ID = 456L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private GitDiffOperations gitDiffOperations;

    private LinkedWorkItemContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LinkedWorkItemContentProvider(
            objectMapper,
            pullRequestRepository,
            issueRepository,
            gitRepositoryManager,
            gitDiffOperations
        );
        // Git disabled by default; the commit-subject scan must no-op. Individual tests enable it.
        lenient().when(gitRepositoryManager.isEnabled()).thenReturn(false);
    }

    private ObjectNode sampleMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", REPO_ID);
        metadata.put("pull_request_id", PR_ID);
        metadata.put("source_branch", "feature/auth-fix");
        metadata.put("target_branch", "main");
        metadata.put("commit_sha", "abc123def456");
        return metadata;
    }

    private ContextRequest.PracticeReviewRequest request(ObjectNode metadata) {
        var job = new AgentJob();
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(99L);
        job.setWorkspace(workspace);
        return new ContextRequest.PracticeReviewRequest(job);
    }

    private Issue issue(int number, String title, String body) {
        Issue issue = new Issue();
        issue.setNumber(number);
        issue.setTitle(title);
        issue.setBody(body);
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl("https://example.com/issues/" + number);
        return issue;
    }

    @Test
    void supportsPracticeReviewOnly() {
        assertThat(provider.supports(request(sampleMetadata()))).isTrue();
    }

    @Test
    void isBestEffort() {
        assertThat(provider.required()).isFalse();
    }

    @Test
    void resolvesClosingRefFromBodyWithAcceptanceCriteriaExcerpt() throws Exception {
        PullRequest pr = new PullRequest();
        pr.setBody("This MR is part of the auth epic.\n\nCloses #42");
        pr.setHeadRefName("feature/auth-fix");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

        Label backend = new Label();
        backend.setName("backend");
        Issue linked = issue(42, "Add token refresh", "Acceptance criteria: the session must refresh silently.");
        linked.setLabels(Set.of(backend));
        linked.setSubIssuesTotal(3);
        linked.setSubIssuesCompleted(1);
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 42)).thenReturn(Optional.of(linked));

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(sampleMetadata()), files);

        assertThat(files).containsKey("context/target/linked_work_items.json");
        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        JsonNode items = root.get("workItems");
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.get("number").asInt()).isEqualTo(42);
        assertThat(item.get("title").asString()).isEqualTo("Add token refresh");
        assertThat(item.get("state").asString()).isEqualTo("OPEN");
        assertThat(item.get("url").asString()).isEqualTo("https://example.com/issues/42");
        assertThat(item.get("closingKeyword").asBoolean()).isTrue();
        assertThat(item.get("bodyExcerpt").asString()).contains("Acceptance criteria");
        assertThat(item.get("labels").get(0).asString()).isEqualTo("backend");
        assertThat(item.get("subIssuesTotal").asInt()).isEqualTo(3);
        assertThat(item.get("subIssuesCompleted").asInt()).isEqualTo(1);

        JsonNode resolvedFrom = root.get("resolvedFrom");
        assertThat(resolvedFrom.get(0).asString()).isEqualTo("body");
    }

    @Test
    void bareMentionIsNotClosing() throws Exception {
        PullRequest pr = new PullRequest();
        pr.setBody("Related to #7 — see context.");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 7)).thenReturn(
            Optional.of(issue(7, "Background", "Some background"))
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(sampleMetadata()), files);

        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        assertThat(root.get("workItems").get(0).get("closingKeyword").asBoolean()).isFalse();
    }

    @Test
    void resolvesIssueIdFromBranchSlugWhenNoBodyRef() throws Exception {
        PullRequest pr = new PullRequest();
        pr.setBody("No references in body.");
        pr.setHeadRefName("feat/18-improve-logging");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 18)).thenReturn(
            Optional.of(issue(18, "Improve logging", "criteria"))
        );

        ObjectNode metadata = sampleMetadata();
        metadata.put("source_branch", "feat/18-improve-logging");

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(metadata), files);

        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        assertThat(root.get("workItems").get(0).get("number").asInt()).isEqualTo(18);
        assertThat(root.get("resolvedFrom").toString()).contains("branch");
    }

    @Test
    void excerptIsCappedAt600Chars() throws Exception {
        PullRequest pr = new PullRequest();
        pr.setBody("Fixes #5");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        String longBody = "x".repeat(2000);
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 5)).thenReturn(
            Optional.of(issue(5, "Big", longBody))
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(sampleMetadata()), files);

        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        assertThat(root.get("workItems").get(0).get("bodyExcerpt").asString()).hasSize(600);
    }

    @Test
    void capsAtEightItems() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            body.append("Closes #").append(i).append(' ');
        }
        PullRequest pr = new PullRequest();
        pr.setBody(body.toString());
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        when(issueRepository.findByRepositoryIdAndNumber(eq(REPO_ID), anyLongAsInt())).thenAnswer(inv -> {
            int n = inv.getArgument(1);
            return Optional.of(issue(n, "Issue " + n, "criteria " + n));
        });

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(sampleMetadata()), files);

        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        assertThat(root.get("workItems")).hasSize(LinkedWorkItemContentProvider.MAX_ITEMS);
    }

    @Test
    void abstainsWhenNoReferences() {
        PullRequest pr = new PullRequest();
        pr.setBody("A clean description with no issue references.");
        pr.setHeadRefName("feature/no-refs");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

        ObjectNode metadata = sampleMetadata();
        metadata.put("source_branch", "feature/no-refs");

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(metadata), files);

        assertThat(files).doesNotContainKey("context/target/linked_work_items.json");
    }

    @Test
    void abstainsWhenReferencedIssueNotFound() {
        PullRequest pr = new PullRequest();
        pr.setBody("Closes #999");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 999)).thenReturn(Optional.empty());

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(sampleMetadata()), files);

        assertThat(files).doesNotContainKey("context/target/linked_work_items.json");
    }

    @Test
    void abstainsWhenMetadataMissing() {
        var job = new AgentJob();
        var req = new ContextRequest.PracticeReviewRequest(job);

        Map<String, byte[]> files = new LinkedHashMap<>();
        assertThatCode(() -> provider.contribute(req, files)).doesNotThrowAnyException();
        assertThat(files).isEmpty();
    }

    @Test
    void abstainsWhenRepositoryIdMissing() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", PR_ID);

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(metadata), files);

        assertThat(files).doesNotContainKey("context/target/linked_work_items.json");
    }

    @Test
    void doesNotThrowWhenRepositoryQueryFails() {
        PullRequest pr = new PullRequest();
        pr.setBody("Closes #42");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 42)).thenThrow(new RuntimeException("DB down"));

        Map<String, byte[]> files = new LinkedHashMap<>();
        assertThatCode(() -> provider.contribute(request(sampleMetadata()), files)).doesNotThrowAnyException();
        assertThat(files).isEmpty();
    }

    @Test
    void resolvesFromCommitSubjectsWhenGitEnabled() throws Exception {
        // No body/branch refs — the only signal is the commit subject.
        PullRequest pr = new PullRequest();
        pr.setBody("Implementation only.");
        pr.setHeadRefName("feature/plain");
        when(pullRequestRepository.findByIdWithAllForGate(PR_ID)).thenReturn(Optional.of(pr));

        when(gitRepositoryManager.isEnabled()).thenReturn(true);
        when(gitRepositoryManager.isRepositoryCloned(REPO_ID)).thenReturn(true);
        when(gitRepositoryManager.getRepositoryPath(REPO_ID)).thenReturn(java.nio.file.Path.of("/tmp/repo/123"));
        when(
            gitDiffOperations.resolveDiffRange(
                java.nio.file.Path.of("/tmp/repo/123"),
                "main",
                "feature/plain",
                "abc123def456"
            )
        ).thenReturn(new String[] { "base", "head" });
        var commit = new GitRepositoryManager.CommitInfo(
            "sha1",
            "fix: resolve crash, fixes #77",
            null,
            "Author",
            "author@example.com",
            java.time.Instant.now(),
            "Author",
            "author@example.com",
            java.time.Instant.now(),
            1,
            0,
            1,
            java.util.List.of(),
            java.util.List.of()
        );
        when(gitRepositoryManager.walkCommits(REPO_ID, "base", "head")).thenReturn(java.util.List.of(commit));
        when(issueRepository.findByRepositoryIdAndNumber(REPO_ID, 77)).thenReturn(
            Optional.of(issue(77, "Crash on launch", "criteria"))
        );

        ObjectNode metadata = sampleMetadata();
        metadata.put("source_branch", "feature/plain");

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(request(metadata), files);

        JsonNode root = objectMapper.readTree(files.get("context/target/linked_work_items.json"));
        assertThat(root.get("workItems").get(0).get("number").asInt()).isEqualTo(77);
        assertThat(root.get("workItems").get(0).get("closingKeyword").asBoolean()).isTrue();
        assertThat(root.get("resolvedFrom").toString()).contains("commits");
    }

    /** Mockito {@code anyInt()} via a named helper for readability where the repo takes an int number. */
    private static int anyLongAsInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}

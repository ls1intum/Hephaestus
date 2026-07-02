package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.milestone.Milestone;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class WorkspaceInventoryContentSourceTest extends BaseUnitTest {

    private static final long REPO_ID = 123L;
    private static final String OUTPUT = "inputs/context/project_inventory.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    private WorkspaceInventoryContentSource provider;

    @BeforeEach
    void setUp() {
        provider = new WorkspaceInventoryContentSource(objectMapper, issueRepository, pullRequestRepository);
        lenient()
            .when(issueRepository.findIssueInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class)))
            .thenReturn(List.of());
        lenient()
            .when(pullRequestRepository.findPullRequestInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class)))
            .thenReturn(List.of());
    }

    // --- helpers ---

    private User user(String login) {
        User u = new User();
        u.setLogin(login);
        return u;
    }

    private Issue issue(int number, String title, Issue.State state, String author) {
        Issue i = new Issue();
        i.setNumber(number);
        i.setTitle(title);
        i.setState(state);
        i.setHtmlUrl("https://example.com/issues/" + number);
        if (author != null) {
            i.setAuthor(user(author));
        }
        return i;
    }

    private Milestone milestone(String title) {
        Milestone ms = new Milestone();
        ms.setTitle(title);
        return ms;
    }

    private PullRequest pr(int number, String title, Issue.State state, boolean draft) {
        PullRequest p = new PullRequest();
        p.setNumber(number);
        p.setTitle(title);
        p.setState(state);
        p.setDraft(draft);
        p.setHtmlUrl("https://example.com/pull/" + number);
        return p;
    }

    private ContextRequest.IssueReviewRequest issueRequest(int focalNumber) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("repository_id", REPO_ID);
        meta.put("repository_full_name", "acme/widgets");
        meta.put("issue_number", focalNumber);
        var job = new AgentJob();
        job.setMetadata(meta);
        return new ContextRequest.IssueReviewRequest(job);
    }

    private ContextRequest.PracticeReviewRequest prRequest(int focalNumber) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("repository_id", REPO_ID);
        meta.put("repository_full_name", "acme/widgets");
        meta.put("pr_number", focalNumber);
        var job = new AgentJob();
        job.setMetadata(meta);
        return new ContextRequest.PracticeReviewRequest(job);
    }

    // --- tests ---

    @Test
    void supportsBothReviewFlows() {
        assertThat(provider.supports(issueRequest(1))).isTrue();
        assertThat(provider.supports(prRequest(1))).isTrue();
    }

    @Test
    void isBestEffort() {
        assertThat(provider.required()).isFalse();
    }

    @Test
    void listsEveryIssueAndPullRequestExcludingTheFocalIssue() throws Exception {
        Issue withMilestone = issue(12, "Login fails on Safari", Issue.State.OPEN, "bob");
        withMilestone.setMilestone(milestone("Sprint 7"));
        when(issueRepository.findIssueInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class))).thenReturn(
            List.of(
                issue(99, "Focal issue under review", Issue.State.OPEN, "alice"),
                withMilestone,
                issue(7, "Old bug", Issue.State.CLOSED, null)
            )
        );
        when(pullRequestRepository.findPullRequestInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class))).thenReturn(
            List.of(pr(70, "Fix login handler", Issue.State.MERGED, false))
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(issueRequest(99), files);

        assertThat(files).containsKey(OUTPUT);
        JsonNode root = objectMapper.readTree(files.get(OUTPUT));
        assertThat(root.get("repository").asString()).isEqualTo("acme/widgets");
        assertThat(root.get("focal").get("type").asString()).isEqualTo("ISSUE");
        assertThat(root.get("focal").get("number").asInt()).isEqualTo(99);

        JsonNode issues = root.get("issues");
        // The focal issue #99 is excluded; the other two remain.
        assertThat(issues).hasSize(2);
        assertThat(issues.get(0).get("number").asInt()).isEqualTo(12);
        assertThat(issues.get(0).get("title").asString()).isEqualTo("Login fails on Safari");
        assertThat(issues.get(0).get("state").asString()).isEqualTo("OPEN");
        assertThat(issues.get(0).get("author").asString()).isEqualTo("bob");
        assertThat(issues.get(0).get("url").asString()).isEqualTo("https://example.com/issues/12");
        assertThat(issues.get(0).get("milestone").asString()).isEqualTo("Sprint 7");
        // Issue nodes never carry a draft flag (that is a PR-only field).
        assertThat(issues.get(0).has("isDraft")).isFalse();
        // The second issue has no milestone -> the field is omitted, not null.
        assertThat(issues.get(1).has("milestone")).isFalse();

        JsonNode prs = root.get("pullRequests");
        assertThat(prs).hasSize(1);
        assertThat(prs.get(0).get("number").asInt()).isEqualTo(70);
        assertThat(prs.get(0).get("state").asString()).isEqualTo("MERGED");
        assertThat(prs.get(0).get("isDraft").asBoolean()).isFalse();

        assertThat(root.get("counts").get("issuesListed").asInt()).isEqualTo(2);
        assertThat(root.get("counts").get("pullRequestsListed").asInt()).isEqualTo(1);
        assertThat(root.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void truncatedIsTrueWhenAFullPageReturnsAndFocalExclusionIsCountedOff() throws Exception {
        // A full MAX_PER_TYPE page (one row focal) → emitted is one short of the cap, but the listing is
        // NOT exhaustive: truncated must be derived from the PRE-exclusion page size, not the emitted count,
        // so absence-of-match cannot be read as uniqueness.
        List<Issue> fullPage = new java.util.ArrayList<>();
        fullPage.add(issue(1, "Focal issue under review", Issue.State.OPEN, "alice"));
        for (int n = 2; n <= WorkspaceInventoryContentSource.MAX_PER_TYPE; n++) {
            fullPage.add(issue(n, "Issue " + n, Issue.State.OPEN, "bob"));
        }
        when(issueRepository.findIssueInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class))).thenReturn(fullPage);

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(issueRequest(1), files);

        JsonNode root = objectMapper.readTree(files.get(OUTPUT));
        assertThat(root.get("truncated").asBoolean()).isTrue();
        // Focal #1 is excluded from the emitted list, so the count is one below the cap even though the page was full.
        assertThat(root.get("counts").get("issuesListed").asInt()).isEqualTo(
            WorkspaceInventoryContentSource.MAX_PER_TYPE - 1
        );
    }

    @Test
    void excludesTheFocalPullRequestInPrFlow() throws Exception {
        when(pullRequestRepository.findPullRequestInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class))).thenReturn(
            List.of(
                pr(42, "The PR under review", Issue.State.OPEN, false),
                pr(41, "Another PR", Issue.State.OPEN, true)
            )
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(prRequest(42), files);

        JsonNode root = objectMapper.readTree(files.get(OUTPUT));
        assertThat(root.get("focal").get("type").asString()).isEqualTo("PULL_REQUEST");
        JsonNode prs = root.get("pullRequests");
        assertThat(prs).hasSize(1);
        assertThat(prs.get(0).get("number").asInt()).isEqualTo(41);
        assertThat(prs.get(0).get("isDraft").asBoolean()).isTrue();
    }

    @Test
    void writesNothingWhenRepositoryHasNoArtifacts() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(issueRequest(1), files);
        assertThat(files).doesNotContainKey(OUTPUT);
    }

    @Test
    void writesNothingWhenRepositoryIdMissing() {
        var job = new AgentJob();
        job.setMetadata(objectMapper.createObjectNode());
        Map<String, byte[]> files = new LinkedHashMap<>();
        provider.contribute(new ContextRequest.IssueReviewRequest(job), files);
        assertThat(files).doesNotContainKey(OUTPUT);
    }

    @Test
    void neverThrowsOnRepositoryFailure() {
        when(issueRepository.findIssueInventoryByRepositoryId(eq(REPO_ID), any(Pageable.class))).thenThrow(
            new RuntimeException("db down")
        );
        Map<String, byte[]> files = new LinkedHashMap<>();
        assertThatCode(() -> provider.contribute(issueRequest(1), files)).doesNotThrowAnyException();
        assertThat(files).doesNotContainKey(OUTPUT);
    }
}

package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.milestone.Milestone;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class IssueContentProviderTest extends BaseUnitTest {

    private static final long ISSUE_ID = 777L;
    private static final String METADATA_KEY = "inputs/context/metadata.json";
    private static final String COMMENTS_KEY = "inputs/context/comments.json";
    private static final String SUMMARY_KEY = "inputs/context/issue_summary.md";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IssueRepository issueRepository;

    private IssueContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new IssueContentProvider(objectMapper, issueRepository);
    }

    // ---- Fixtures ----------------------------------------------------------

    private ObjectNode sampleMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("issue_id", ISSUE_ID);
        return metadata;
    }

    private AgentJob jobWith(ObjectNode metadata) {
        var job = new AgentJob();
        job.setId(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        job.setMetadata(metadata);
        return job;
    }

    private ContextRequest.IssueReviewRequest request(ObjectNode metadata) {
        return new ContextRequest.IssueReviewRequest(jobWith(metadata));
    }

    private User user(String login) {
        User u = new User();
        u.setLogin(login);
        return u;
    }

    private Label label(String name) {
        Label l = new Label();
        l.setName(name);
        return l;
    }

    private IssueComment comment(String authorLogin, String body, Instant createdAt) {
        IssueComment c = new IssueComment();
        c.setBody(body);
        if (authorLogin != null) {
            c.setAuthor(user(authorLogin));
        }
        c.setCreatedAt(createdAt);
        return c;
    }

    /** A fully-populated, CLOSED issue with labels, assignees, a milestone, and a sub-issue rollup. */
    private Issue richIssue() {
        Issue issue = new Issue();
        issue.setId(ISSUE_ID);
        issue.setNumber(123);
        issue.setTitle("Tighten the practice catalogue");
        issue.setBody("Make the catalogue honest.");
        issue.setState(Issue.State.CLOSED);
        issue.setStateReason(Issue.StateReason.COMPLETED);
        issue.setHtmlUrl("https://github.com/owner/repo/issues/123");
        issue.setLocked(true);
        issue.setCommentsCount(2);
        issue.setSubIssuesTotal(4);
        issue.setSubIssuesCompleted(3);
        issue.setAuthor(user("felix"));

        Repository repo = new Repository();
        repo.setNameWithOwner("owner/repo");
        issue.setRepository(repo);

        Milestone milestone = new Milestone();
        milestone.setTitle("v1.0");
        issue.setMilestone(milestone);

        // Set iteration order is incidental; the provider sorts label/assignee output.
        issue.setLabels(new java.util.LinkedHashSet<>(List.of(label("zeta"), label("alpha"))));
        issue.setAssignees(new java.util.LinkedHashSet<>(List.of(user("bob"), user("alice"))));

        return issue;
    }

    // ---- Supports ----------------------------------------------------------

    @Nested
    class Supports {

        @Test
        void supportsIssueReviewRequest() {
            assertThat(provider.supports(request(sampleMetadata()))).isTrue();
        }
    }

    // ---- metadata.json -----------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void writesIssueMetadataFields() throws Exception {
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(richIssue()));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey(METADATA_KEY);
            JsonNode meta = objectMapper.readTree(files.get(METADATA_KEY));
            assertThat(meta.get("issue_number").asInt()).isEqualTo(123);
            assertThat(meta.get("title").asString()).isEqualTo("Tighten the practice catalogue");
            assertThat(meta.get("state").asString()).isEqualTo("CLOSED");
            assertThat(meta.get("state_reason").asString()).isEqualTo("COMPLETED");
            assertThat(meta.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(meta.get("author").asString()).isEqualTo("felix");
            assertThat(meta.get("is_locked").asBoolean()).isTrue();
            assertThat(meta.get("comments_count").asInt()).isEqualTo(2);
            assertThat(meta.get("milestone").asString()).isEqualTo("v1.0");
        }

        @Test
        void rollsUpSubIssueCounts() throws Exception {
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(richIssue()));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode meta = objectMapper.readTree(files.get(METADATA_KEY));
            assertThat(meta.get("sub_issues_total").asInt()).isEqualTo(4);
            assertThat(meta.get("sub_issues_completed").asInt()).isEqualTo(3);
        }

        @Test
        void sortsLabelsAlphabetically() throws Exception {
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(richIssue()));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode labels = objectMapper.readTree(files.get(METADATA_KEY)).get("labels");
            assertThat(labels).hasSize(2);
            assertThat(labels.get(0).asString()).isEqualTo("alpha");
            assertThat(labels.get(1).asString()).isEqualTo("zeta");
        }

        @Test
        void sortsAssigneeLoginsAlphabetically() throws Exception {
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(richIssue()));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode assignees = objectMapper.readTree(files.get(METADATA_KEY)).get("assignees");
            assertThat(assignees).hasSize(2);
            assertThat(assignees.get(0).asString()).isEqualTo("alice");
            assertThat(assignees.get(1).asString()).isEqualTo("bob");
        }

        @Test
        void emitsNullStateReasonWhenUnset() throws Exception {
            Issue issue = richIssue();
            issue.setStateReason(null);
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode meta = objectMapper.readTree(files.get(METADATA_KEY));
            assertThat(meta.get("state_reason").isNull()).isTrue();
            assertThat(meta.get("milestone").asString()).isEqualTo("v1.0");
        }
    }

    // ---- comments.json -----------------------------------------------------

    @Nested
    class Comments {

        @Test
        void ordersThreadByCreatedAtAscending() throws Exception {
            Issue issue = richIssue();
            // Inserted out of order; provider must sort ascending by createdAt.
            IssueComment newer = comment("alice", "second", Instant.parse("2025-06-02T10:00:00Z"));
            IssueComment older = comment("bob", "first", Instant.parse("2025-06-01T10:00:00Z"));
            issue.setComments(new java.util.LinkedHashSet<>(List.of(newer, older)));
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode comments = objectMapper.readTree(files.get(COMMENTS_KEY));
            assertThat(comments).hasSize(2);
            assertThat(comments.get(0).get("body").asString()).isEqualTo("first");
            assertThat(comments.get(0).get("author").asString()).isEqualTo("bob");
            assertThat(comments.get(0).get("created_at").asString()).isEqualTo("2025-06-01T10:00:00Z");
            assertThat(comments.get(1).get("body").asString()).isEqualTo("second");
        }

        @Test
        void emitsNullAuthorWhenCommentHasNoAuthor() throws Exception {
            Issue issue = richIssue();
            issue.setComments(
                new java.util.LinkedHashSet<>(List.of(comment(null, "anon", Instant.parse("2025-06-01T10:00:00Z"))))
            );
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode comments = objectMapper.readTree(files.get(COMMENTS_KEY));
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).get("author").isNull()).isTrue();
        }

        @Test
        void truncatesToMaxCommentsKeepingMostRecent() throws Exception {
            Issue issue = richIssue();
            var thread = new java.util.LinkedHashSet<IssueComment>();
            int overflow = IssueContentProvider.MAX_COMMENTS + 50;
            Instant base = Instant.parse("2025-01-01T00:00:00Z");
            for (int i = 0; i < overflow; i++) {
                // createdAt strictly increasing so "most recent" is unambiguous.
                thread.add(comment("u" + i, "Comment " + i, base.plusSeconds(i)));
            }
            issue.setComments(thread);
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            JsonNode comments = objectMapper.readTree(files.get(COMMENTS_KEY));
            assertThat(comments).hasSize(IssueContentProvider.MAX_COMMENTS);
            // The 50 oldest were dropped; the first kept comment is index 50.
            assertThat(comments.get(0).get("body").asString()).isEqualTo("Comment 50");
            assertThat(comments.get(comments.size() - 1).get("body").asString()).isEqualTo("Comment " + (overflow - 1));
        }
    }

    // ---- issue_summary.md --------------------------------------------------

    @Nested
    class Summary {

        @Test
        void writesIssueSummaryMarkdown() {
            Issue issue = richIssue();
            issue.setComments(
                new java.util.LinkedHashSet<>(List.of(comment("bob", "first", Instant.parse("2025-06-01T10:00:00Z"))))
            );
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            assertThat(files).containsKey(SUMMARY_KEY);
            String md = new String(files.get(SUMMARY_KEY), StandardCharsets.UTF_8);
            assertThat(md).contains("# Issue #123 — Tighten the practice catalogue");
            assertThat(md).contains("**State:** CLOSED (COMPLETED)");
            assertThat(md).contains("**Sub-issues:** 3/4 completed");
            assertThat(md).contains("## Discussion (1 comments)");
            assertThat(md).contains("**bob** wrote:");
        }

        @Test
        void rendersNullAuthorAsUnknownInSummary() {
            // A null-author comment must render as the literal "**unknown** wrote:", never "**null** wrote:".
            Issue issue = richIssue();
            issue.setComments(
                new java.util.LinkedHashSet<>(List.of(comment(null, "anon", Instant.parse("2025-06-01T10:00:00Z"))))
            );
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            String md = new String(files.get(SUMMARY_KEY), StandardCharsets.UTF_8);
            assertThat(md).contains("**unknown** wrote:");
            assertThat(md).doesNotContain("**null** wrote:");
        }

        @Test
        void discussionHeaderReflectsTruncatedCount() {
            // The summary uses the post-truncation list, so its "## Discussion (N comments)" header must
            // report the capped MAX_COMMENTS, not the pre-truncation total.
            Issue issue = richIssue();
            var thread = new java.util.LinkedHashSet<IssueComment>();
            int overflow = IssueContentProvider.MAX_COMMENTS + 50;
            Instant base = Instant.parse("2025-01-01T00:00:00Z");
            for (int i = 0; i < overflow; i++) {
                thread.add(comment("u" + i, "Comment " + i, base.plusSeconds(i)));
            }
            issue.setComments(thread);
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            String md = new String(files.get(SUMMARY_KEY), StandardCharsets.UTF_8);
            assertThat(md).contains("## Discussion (" + IssueContentProvider.MAX_COMMENTS + " comments)");
            assertThat(md).doesNotContain("## Discussion (" + overflow + " comments)");
        }
    }

    // ---- Abstention paths --------------------------------------------------

    @Nested
    class Abstention {

        @Test
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            assertThatThrownBy(() ->
                provider.contribute(new ContextRequest.IssueReviewRequest(job), new LinkedHashMap<>())
            )
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Job has no metadata");
        }

        @Test
        void throwsWhenIssueIdAbsentFromMetadata() {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("something_else", 1);

            assertThatThrownBy(() -> provider.contribute(request(metadata), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("metadata field: issue_id");
        }

        @Test
        void throwsWhenIssueIdIsExplicitNull() {
            // {"issue_id": null}: has("issue_id") is true but the field is null. The strict reader must
            // reject it rather than letting NullNode.asLong() default to 0 and surface as the misleading
            // "Issue not found: issueId=0".
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.putNull("issue_id");

            assertThatThrownBy(() -> provider.contribute(request(metadata), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("metadata field: issue_id");
        }

        @Test
        void throwsWhenIssueIdIsNonNumeric() {
            // A non-numeric issue_id must fail at metadata parse with the "metadata field" message, not
            // resolve to 0 and surface downstream as "Issue not found: issueId=0".
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("issue_id", "not-a-number");

            assertThatThrownBy(() -> provider.contribute(request(metadata), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("metadata field: issue_id");
        }

        @Test
        void throwsWhenIssueNotFound() {
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provider.contribute(request(sampleMetadata()), new LinkedHashMap<>()))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Issue not found");
        }

        @Test
        void rendersEmptyBodyPlaceholderInSummary() {
            Issue issue = richIssue();
            issue.setBody(null);
            when(issueRepository.findByIdWithRepository(ISSUE_ID)).thenReturn(Optional.of(issue));

            Map<String, byte[]> files = new LinkedHashMap<>();
            provider.contribute(request(sampleMetadata()), files);

            String md = new String(files.get(SUMMARY_KEY), StandardCharsets.UTF_8);
            assertThat(md).contains("## Description\n\n_(empty)_");
            // An empty body still produces a valid metadata body field (empty string, not null).
            assertThat(files).containsKey(METADATA_KEY);
        }
    }
}

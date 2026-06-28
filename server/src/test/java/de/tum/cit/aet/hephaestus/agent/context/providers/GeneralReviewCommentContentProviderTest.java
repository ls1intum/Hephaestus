package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class GeneralReviewCommentContentProviderTest extends BaseUnitTest {

    private static final String FILE_KEY = "inputs/context/general_comments.json";
    private static final Long PR_ID = 456L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IssueCommentRepository issueCommentRepository;

    private GeneralReviewCommentContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GeneralReviewCommentContentProvider(objectMapper, issueCommentRepository);
        lenient().when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(any())).thenReturn(List.of());
    }

    private ObjectNode metadataWithPr() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);
        metadata.put("pull_request_id", PR_ID);
        return metadata;
    }

    private ContextRequest.PracticeReviewRequest request(ObjectNode metadata) {
        AgentJob job = new AgentJob();
        job.setMetadata(metadata);
        Workspace workspace = new Workspace();
        workspace.setId(99L);
        job.setWorkspace(workspace);
        return new ContextRequest.PracticeReviewRequest(job);
    }

    private IssueComment comment(String login, String body, Instant createdAt) {
        IssueComment c = new IssueComment();
        c.setBody(body);
        if (login != null) {
            User u = new User();
            u.setLogin(login);
            c.setAuthor(u);
        }
        c.setCreatedAt(createdAt);
        return c;
    }

    @Test
    void contribute_noPrId_writesNothing() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadata), files);

        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_noComments_writesNothing() {
        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_generalDiscussion_emittedWithAuthorAndBody() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                comment(
                    "reviewer-a",
                    "I think this is always going to pass since confidence is always >= 0.3",
                    Instant.parse("2025-06-01T10:00:00Z")
                ),
                comment("author-x", "added confidence scoring to address this", Instant.parse("2025-06-01T11:00:00Z"))
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        assertThat(files).containsKey(FILE_KEY);
        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("count").asInt()).isEqualTo(2);
        JsonNode first = out.get("comments").get(0);
        assertThat(first.get("author").asString()).isEqualTo("reviewer-a");
        assertThat(first.get("body").asString()).contains("confidence");
        assertThat(first.get("createdAt").asString()).isEqualTo("2025-06-01T10:00:00Z");
    }

    @Test
    void contribute_excludesHephaestusOwnComments() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                comment(
                    "bot",
                    "<!-- hephaestus:practice-review:abc --> 2 issues to fix before merging",
                    Instant.parse("2025-06-01T09:00:00Z")
                ),
                comment(
                    "reviewer-b",
                    "Nit: split persistence out so each unit is testable",
                    Instant.parse("2025-06-01T10:00:00Z")
                )
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        // The Hephaestus marker comment is dropped; only the human reviewer comment survives.
        assertThat(out.get("count").asInt()).isEqualTo(1);
        assertThat(out.get("comments").get(0).get("author").asString()).isEqualTo("reviewer-b");
    }

    @Test
    void contribute_onlyHephaestusComments_writesNothing() {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                comment("bot", "<!-- hephaestus:practice-review:abc --> summary", Instant.parse("2025-06-01T09:00:00Z"))
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        // Only the bot's own comment was present — emit nothing so reviewer-craft keeps empty-context abstention.
        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_neverThrows_onRepositoryFailure() {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenThrow(
            new RuntimeException("db down")
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        assertThatCode(() -> provider.contribute(request(metadataWithPr()), files)).doesNotThrowAnyException();
        assertThat(files).doesNotContainKey(FILE_KEY);
    }

    @Test
    void contribute_overCap_keepsNewestAndFlagsTruncated() throws Exception {
        // A6: the query is ORDER BY createdAt ASC (oldest first). On truncation the provider must keep the MOST
        // RECENT MAX_COMMENTS — keeping the oldest head would drop the late approval/resolution and manufacture a
        // false "rubber-stamp" verdict. Build MAX_COMMENTS + 5 comments; only the newest MAX_COMMENTS survive.
        int total = GeneralReviewCommentContentProvider.MAX_COMMENTS + 5;
        java.util.List<IssueComment> comments = new java.util.ArrayList<>();
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (int i = 0; i < total; i++) {
            // body encodes the sequence index so we can assert WHICH comments survived.
            comments.add(comment("reviewer-" + i, "comment-" + i, base.plusSeconds(i)));
        }
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(comments);

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("count").asInt()).isEqualTo(GeneralReviewCommentContentProvider.MAX_COMMENTS);
        assertThat(out.get("truncated").asBoolean()).isTrue();
        // The oldest (comment-0) is dropped; the newest (last index) survives and leads the kept tail.
        JsonNode bodies = out.get("comments");
        assertThat(bodies.get(0).get("body").asString()).isEqualTo("comment-5"); // first kept = total-MAX
        assertThat(bodies.get(bodies.size() - 1).get("body").asString()).isEqualTo("comment-" + (total - 1));
        // The dropped oldest must not appear anywhere.
        for (JsonNode c : bodies) {
            assertThat(c.get("body").asString()).isNotEqualTo("comment-0");
        }
    }

    @Test
    void contribute_underCap_flagsNotTruncated() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(comment("reviewer-a", "looks good", Instant.parse("2025-06-01T10:00:00Z")))
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void contribute_nullAuthor_omitsAuthorKey() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(comment(null, "anonymous note", Instant.parse("2025-06-01T10:00:00Z")))
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode first = objectMapper.readTree(files.get(FILE_KEY)).get("comments").get(0);
        // login()==null → the author key is omitted entirely, never serialised as JSON null.
        assertThat(first.has("author")).isFalse();
        assertThat(first.get("body").asString()).isEqualTo("anonymous note");
    }

    @Test
    void contribute_nullCreatedAt_omitsCreatedAtKey() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(comment("reviewer-a", "no timestamp", null))
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode first = objectMapper.readTree(files.get(FILE_KEY)).get("comments").get(0);
        assertThat(first.has("createdAt")).isFalse();
        assertThat(first.get("author").asString()).isEqualTo("reviewer-a");
    }

    @Test
    void contribute_blankBody_isSkipped() throws Exception {
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                comment("reviewer-a", "   ", Instant.parse("2025-06-01T09:00:00Z")),
                comment("reviewer-b", "real feedback", Instant.parse("2025-06-01T10:00:00Z"))
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        // The blank-body comment is dropped; only the substantive comment survives.
        assertThat(out.get("count").asInt()).isEqualTo(1);
        assertThat(out.get("comments").get(0).get("author").asString()).isEqualTo("reviewer-b");
    }

    @Test
    void contribute_hyphenFormDiffNoteMarker_isNotExcluded() throws Exception {
        // HEPHAESTUS_MARKER is the colon form `<!-- hephaestus:` only. The hyphen-form diff-note marker
        // `<!-- hephaestus-diff-note -->` is NOT matched here — and correctly so: diff notes are stored as
        // PullRequestReviewComment, never IssueComment, so this provider (IssueComment-only) never sees them.
        // This test pins that storage-split boundary so a marker-narrowing regression is caught.
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                comment(
                    "reviewer-a",
                    "<!-- hephaestus-diff-note --> human follow-up",
                    Instant.parse("2025-06-01T10:00:00Z")
                )
            )
        );

        Map<String, byte[]> files = new java.util.HashMap<>();
        provider.contribute(request(metadataWithPr()), files);

        JsonNode out = objectMapper.readTree(files.get(FILE_KEY));
        assertThat(out.get("count").asInt()).isEqualTo(1);
    }

    @Test
    void required_isFalse_bestEffort() {
        assertThat(provider.required()).isFalse();
    }
}

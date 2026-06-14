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
        User u = new User();
        u.setLogin(login);
        c.setAuthor(u);
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
    void required_isFalse_bestEffort() {
        assertThat(provider.required()).isFalse();
    }
}

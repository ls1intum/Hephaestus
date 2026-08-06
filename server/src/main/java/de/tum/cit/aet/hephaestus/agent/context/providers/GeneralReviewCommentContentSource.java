package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@Order(210)
public class GeneralReviewCommentContentSource implements EvidenceSource {

    private static final SourceKind KIND = new SourceKind("scm.general-review-comments");

    @Override
    public Set<SourceKind> sourceKinds() {
        return Set.of(KIND);
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        return KIND;
    }

    private static final Logger log = LoggerFactory.getLogger(GeneralReviewCommentContentSource.class);

    static final String FILE_NAME = "general_comments.json";

    static final int MAX_COMMENTS = 200;

    static final String HEPHAESTUS_MARKER = "<!-- hephaestus:";

    private final ObjectMapper objectMapper;
    private final IssueCommentRepository issueCommentRepository;

    public GeneralReviewCommentContentSource(ObjectMapper objectMapper, IssueCommentRepository issueCommentRepository) {
        this.objectMapper = objectMapper;
        this.issueCommentRepository = issueCommentRepository;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    /**
     * Reports the truncation the payload already records.
     *
     * <p>The default capture leaves completeness to the catalog, which permits COMPLETE for this source —
     * so a pull request past the comment limit would be described as holding all of them.
     */
    @Override
    public EvidenceContribution capture(ContextRequest request, Set<SourceKind> selectedKinds) {
        EvidenceContribution captured = EvidenceSource.super.capture(request, selectedKinds);
        byte[] emitted = captured.files().get(OUTPUT_PREFIX + FILE_NAME);
        if (!selectedKinds.contains(KIND) || emitted == null) {
            return captured;
        }
        boolean truncated = objectMapper.readTree(emitted).path("truncated").asBoolean(false);
        return new EvidenceContribution(
            captured.files(),
            Map.of(KIND, truncated ? SourceCompleteness.PARTIAL : SourceCompleteness.COMPLETE),
            captured.immutableIdentities(),
            captured.observedAt(),
            captured.sourceEffectiveAt(),
            captured.contentStates()
        );
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return;
        }
        try {
            AgentJob job = pr.job();
            JsonNode m = job.getMetadata();
            // As in ReviewThreadContentSource: a missing key is a malformed job, and "no review
            // comments" is a claim the model would otherwise take as established.
            if (m == null || m.isNull() || m.isMissingNode()) {
                throw new EvidenceCollectionException("Review-comment collection has no job metadata", null);
            }
            Long pullRequestId = MetaJson.optLong(m, "pull_request_id");
            if (pullRequestId == null) {
                throw new EvidenceCollectionException("Review-comment collection has no pull_request_id", null);
            }

            List<IssueComment> comments = new java.util.ArrayList<>(
                issueCommentRepository.findRecentHumanByIssueIdWithAuthor(
                    pullRequestId,
                    HEPHAESTUS_MARKER,
                    PageRequest.of(0, MAX_COMMENTS + 1)
                )
            );
            comments.removeIf(comment -> {
                String body = comment == null ? null : comment.getBody();
                return body == null || body.isBlank() || body.contains(HEPHAESTUS_MARKER);
            });
            if (comments.isEmpty()) {
                return;
            }
            if (comments.size() > MAX_COMMENTS + 1) {
                comments = new java.util.ArrayList<>(comments.subList(0, MAX_COMMENTS + 1));
            }
            boolean truncated = comments.size() > MAX_COMMENTS;
            if (truncated) comments.remove(comments.size() - 1);
            comments.sort(
                Comparator.comparing(IssueComment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            );

            ArrayNode commentArray = objectMapper.createArrayNode();
            for (IssueComment c : comments) {
                commentArray.add(toComment(c, c.getBody()));
            }
            int emitted = commentArray.size();

            ObjectNode root = objectMapper.createObjectNode();
            root.set("comments", commentArray);
            root.put("count", emitted);
            root.put("truncated", truncated);
            files.put(OUTPUT_PREFIX + FILE_NAME, objectMapper.writeValueAsBytes(root));
            log.info("GeneralReviewComments: prId={} emitted={} truncated={}", pullRequestId, emitted, truncated);
        } catch (Exception e) {
            throw new EvidenceCollectionException("General-review-comment collection failed", e);
        }
    }

    private ObjectNode toComment(IssueComment c, String body) {
        ObjectNode node = objectMapper.createObjectNode();
        String author = login(c.getAuthor());
        if (author != null) {
            node.put("author", author);
        }
        node.put("body", body);
        if (c.getCreatedAt() != null) {
            node.put("createdAt", c.getCreatedAt().toString());
        }
        return node;
    }

    private static String login(User user) {
        if (user == null) {
            return null;
        }
        String login = user.getLogin();
        return (login != null && !login.isBlank()) ? login : null;
    }
}

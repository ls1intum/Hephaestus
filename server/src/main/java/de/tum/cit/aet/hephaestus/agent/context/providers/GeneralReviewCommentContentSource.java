package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Best-effort provider that materialises the <em>general (conversation-tab) review discussion</em>
 * of a merge request into {@code inputs/context/general_comments.json}.
 *
 * <p>{@code comments.json} ({@link PullRequestContentSource}) models only position-anchored inline
 * notes; GitLab routes every <em>position-less</em> note (conversation-tab comment, non-anchored
 * suggestion) to {@code IssueComment} storage. Without those, the reviewer-craft practices see an
 * empty inline thread and can fire a false "rubber-stamp" NEGATIVE on an MR whose real review lives
 * in the conversation tab. A {@code PullRequest} IS an {@code Issue}, so its conversation notes are
 * {@code IssueComment} rows keyed by the same id. Output is a judgement-free fact sheet:
 * {@code {"comments":[{"author","body","createdAt"}],"count"}}.
 *
 * <p><b>Self-exclusion.</b> Hephaestus' own MR comments carry the {@code <!-- hephaestus:... -->}
 * marker and are dropped so the agent never assesses its own output as reviewer input; author/bot
 * exclusion stays in the criteria.
 *
 * <p>Best-effort ({@link #required()} == {@code false}): a missing PR id, absent rows, or any
 * failure degrades to writing nothing and never aborts the job, preserving the practices'
 * empty-context behaviour.
 */
@Component
@Order(210)
public class GeneralReviewCommentContentSource implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(GeneralReviewCommentContentSource.class);

    /** Output filename under {@link ContentSource#OUTPUT_PREFIX}. */
    static final String FILE_NAME = "general_comments.json";

    /** Cap on comments materialised — keeps the artefact a few KB even on a very chatty MR. */
    static final int MAX_COMMENTS = 200;

    /**
     * Namespace prefix embedded in every Hephaestus-authored MR comment (the practice-review summary AND the
     * re-review ping). A general comment containing it is the bot's own output and must never be surfaced as
     * reviewer input. Matches the whole {@code <!-- hephaestus:* -->} namespace so a new marker can't leak back.
     */
    static final String HEPHAESTUS_MARKER = "<!-- hephaestus:";

    private final ObjectMapper objectMapper;
    private final IssueCommentRepository issueCommentRepository;

    public GeneralReviewCommentContentSource(ObjectMapper objectMapper, IssueCommentRepository issueCommentRepository) {
        this.objectMapper = objectMapper;
        this.issueCommentRepository = issueCommentRepository;
    }

    @Override
    public String originId() {
        return "scm";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return;
        }
        try {
            AgentJob job = pr.job();
            JsonNode m = job.getMetadata();
            if (m == null || m.isNull() || m.isMissingNode()) {
                return;
            }
            Long pullRequestId = MetaJson.optLong(m, "pull_request_id");
            if (pullRequestId == null) {
                return;
            }

            List<IssueComment> comments = issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(pullRequestId);
            if (comments == null || comments.isEmpty()) {
                return;
            }

            // When over the cap keep the MOST RECENT MAX_COMMENTS: the query is oldest-first, and
            // keeping the head would drop the latest approval/resolution on a chatty MR —
            // manufacturing the exact false "rubber-stamp" verdict this provider exists to prevent.
            List<IssueComment> eligible = new ArrayList<>();
            int skippedSelf = 0;
            for (IssueComment c : comments) {
                if (c == null) {
                    continue;
                }
                String body = c.getBody();
                if (body == null || body.isBlank()) {
                    continue;
                }
                if (body.contains(HEPHAESTUS_MARKER)) {
                    skippedSelf++;
                    continue;
                }
                eligible.add(c);
            }

            if (eligible.isEmpty()) {
                // Only the bot's own comment(s) were present — emit nothing rather than a hollow file.
                return;
            }

            boolean truncated = eligible.size() > MAX_COMMENTS;
            List<IssueComment> kept = truncated
                ? eligible.subList(eligible.size() - MAX_COMMENTS, eligible.size())
                : eligible;

            ArrayNode commentArray = objectMapper.createArrayNode();
            for (IssueComment c : kept) {
                commentArray.add(toComment(c, c.getBody()));
            }
            int emitted = commentArray.size();

            ObjectNode root = objectMapper.createObjectNode();
            root.set("comments", commentArray);
            root.put("count", emitted);
            root.put("truncated", truncated);
            files.put(OUTPUT_PREFIX + FILE_NAME, objectMapper.writeValueAsBytes(root));
            log.info("GeneralReviewComments: prId={} emitted={} skippedSelf={}", pullRequestId, emitted, skippedSelf);
        } catch (Exception e) {
            log.warn(
                "GeneralReviewCommentContentSource failed, continuing without general discussion: {}",
                e.getMessage()
            );
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

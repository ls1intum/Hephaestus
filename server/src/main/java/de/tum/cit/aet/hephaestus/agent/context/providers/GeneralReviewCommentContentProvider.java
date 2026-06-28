package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
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
 * Cross-context, best-effort provider that materialises the <em>general (conversation-tab) review
 * discussion</em> of a merge request into {@code inputs/context/general_comments.json}.
 *
 * <p><b>The gap this closes.</b> {@code comments.json} (written by {@link PullRequestContentProvider})
 * is built from {@code PullRequestReviewComment} only — a row that, by construction, carries a diff
 * {@code path} + integer {@code line}. It therefore models ONLY position-anchored inline notes.
 * GitLab routes every <em>position-less</em> note (a conversation-tab comment, a non-anchored
 * suggestion, a design back-and-forth) to {@code IssueComment} storage
 * (see {@code GitLabDiscussionSyncService}: discussions whose notes have {@code position == null} →
 * {@code IssueComment}). No PR-scoped provider ever surfaced those, so the reviewer-craft practices
 * (reviews-substantively, leaves-useful, engaging, reviews-respectfully) saw an empty inline thread
 * and either abstained or — worse — fired a confident "rubber-stamp" NEGATIVE on an APPROVED MR
 * whose real review lived entirely in the conversation tab. A mentor calling a genuinely substantive
 * review a rubber-stamp in front of a student is worse than silence.
 *
 * <p>This provider reads the MR's general comments by issue/PR id (a {@code PullRequest} IS an
 * {@code Issue}, so its conversation notes are {@code IssueComment} rows keyed by the same id) and
 * emits a compact, judgement-free fact sheet (telescope, not cage):
 *
 * <pre>
 * {
 *   "comments": [{"author":..,"body":..,"createdAt":..}],
 *   "count": N
 * }
 * </pre>
 *
 * <p><b>Self-exclusion.</b> Hephaestus posts its own practice-review summary as an MR comment, which
 * also lands in {@code IssueComment}. Those carry the {@code <!-- hephaestus:practice-review:... -->}
 * marker; this provider drops them so the agent never assesses its own output as reviewer input. The
 * author/bot exclusion (author == the MR author, etc.) stays in the criteria, as it already does for
 * {@code comments.json}.
 *
 * <p>Best-effort ({@link #required()} == {@code false}): a missing PR id, absent rows, or any failure
 * degrades to writing nothing and NEVER aborts the job. When there is no general comment at all the
 * file is omitted, so the reviewer-craft practices keep their existing empty-context behaviour.
 */
@Component
@Order(210)
public class GeneralReviewCommentContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(GeneralReviewCommentContentProvider.class);

    /** Output filename under {@link ContentProvider#OUTPUT_PREFIX}. */
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

    public GeneralReviewCommentContentProvider(
        ObjectMapper objectMapper,
        IssueCommentRepository issueCommentRepository
    ) {
        this.objectMapper = objectMapper;
        this.issueCommentRepository = issueCommentRepository;
    }

    @Override
    public String connectorId() {
        return "scm";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    /** Cross-context enrichment: never abort the job if the general discussion cannot be resolved. */
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

            // Collect the eligible (non-blank, non-bot) comments first, then — when over the cap — keep the
            // MOST RECENT MAX_COMMENTS (tail-slice, mirroring PullRequestContentProvider#buildReviewComments).
            // The query is ORDER BY createdAt ASC (oldest first); keeping the head would drop the LATEST
            // approval/resolution on a chatty MR and manufacture a false "rubber-stamp" verdict — the exact
            // failure this provider exists to prevent.
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
                // Only the bot's own comment(s) were present — emit nothing so the reviewer-craft
                // practices keep their empty-context abstention rather than seeing a hollow file.
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
            // Best-effort: cross-context enrichment must never fail the job.
            log.warn(
                "GeneralReviewCommentContentProvider failed, continuing without general discussion: {}",
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

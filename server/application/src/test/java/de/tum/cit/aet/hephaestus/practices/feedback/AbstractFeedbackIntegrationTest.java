package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponseRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.JsonNodeFactory;

public abstract class AbstractFeedbackIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    protected FeedbackRepository feedback;

    @Autowired
    protected FeedbackCopyRepository copies;

    @Autowired
    protected FeedbackBasisRepository basis;

    @Autowired
    protected FeedbackSendRepository sends;

    @Autowired
    protected FeedbackResponseRepository responses;

    @Autowired
    protected FeedbackDecisionRepository decisions;

    @Autowired
    protected ResultRepository results;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected Workspace workspace;
    protected long developerId, itemId;
    protected UUID reviewId;
    protected static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");

    @BeforeEach
    void createFeedbackContext() {
        var owner = persistUser("feedback-owner");
        workspace = createWorkspace("feedback-tests", "Feedback tests", "feedback-org", AccountType.ORG, owner);
        developerId = Objects.requireNonNull(
                ensureAdminMembership(workspace).getAccount().getId());
        itemId = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO item(workspace_id,kind,source_id) VALUES" + " (?,'scm.pull_request',42) RETURNING id",
                Long.class,
                workspace.getId()));
        reviewId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)"
                        + " VALUES (?,?,?,'ITEM','v1','REQUEST','RUNNING',?,?)",
                reviewId,
                workspace.getId(),
                itemId,
                Timestamp.from(AT),
                Timestamp.from(AT));
    }

    protected Feedback piece(FeedbackDestination destination, FeedbackState state, @Nullable Instant deliveredAt) {
        return feedback.saveAndFlush(Feedback.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .reviewId(reviewId)
                .itemId(itemId)
                .developerId(developerId)
                .destination(destination)
                .state(state)
                .body("Add a focused regression test.")
                .createdAt(AT)
                .deliveredAt(deliveredAt)
                .build());
    }

    protected FeedbackCopy summary(Feedback parent, FeedbackCopyState state) {
        UUID id = FeedbackIds.copy(parent.getId(), FeedbackCopyKind.SUMMARY, 0);
        boolean fenced = state == FeedbackCopyState.WRITE_STARTED
                || state == FeedbackCopyState.POSTED
                || state == FeedbackCopyState.RETRACTED;
        boolean shown = state == FeedbackCopyState.POSTED || state == FeedbackCopyState.RETRACTED;
        return copies.saveAndFlush(FeedbackCopy.builder()
                .id(id)
                .workspaceId(workspace.getId())
                .feedbackId(parent.getId())
                .kind(FeedbackCopyKind.SUMMARY)
                .position(0)
                .state(state)
                .writeKey(id.toString())
                .writeMarker(FeedbackIds.writeMarker(id))
                .writeStartedAt(fenced ? AT : null)
                .externalRef(shown ? "copy-" + id : null)
                .postedAt(shown ? AT : null)
                .createdAt(AT)
                .build());
    }

    protected Result result() {
        Long practiceId = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('practice','id'))", Long.class);
        Long versionId =
                jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('practice_version','id'))", Long.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO"
                            + " practice(id,workspace_id,slug,kind,current_version_id,feedback_mode)"
                            + " VALUES (?, ?, ?, 'scm.pull_request', ?, 'AUTOMATIC')",
                    practiceId,
                    workspace.getId(),
                    "test-" + practiceId,
                    versionId);
            jdbc.update(
                    "INSERT INTO"
                            + " practice_version(id,workspace_id,practice_id,number,definition)"
                            + " VALUES (?,?,?,1,?::jsonb)",
                    versionId,
                    workspace.getId(),
                    practiceId,
                    """
                    {"name":"Regression tests","criteria":"Cover failures.","about":"AUTHOR",
                     "reviewOn":{"events":["scm.pull_request.updated"],"state":{}},"appliesWhen":null,
                     "reads":{"contract":"source-use:2.0.0","sources":[{"source":"scm.pull-request.core","need":"COMPLETE"}]},
                     "reviewedBy":{"by":"HEPHAESTUS","humanReason":null,"limits":[]},
                     "script":null,"scan":null,"lineNotes":false,
                     "guidance":{"whyItMatters":null,"whatGoodLooksLike":null,"goingWell":null,"visual":null,"guide":null}}
                    """);
            jdbc.update(
                    "INSERT INTO"
                            + " review_practice(review_id,practice_id,workspace_id,practice_version_id,status)"
                            + " VALUES (?,?,?,?,'READY')",
                    reviewId,
                    practiceId,
                    workspace.getId(),
                    versionId);
            jdbc.update(
                    "INSERT INTO"
                            + " review_item(review_id,practice_id,item_id,workspace_id,item_version,state)"
                            + " VALUES (?,?,?,?,'v1','LISTED')",
                    reviewId,
                    practiceId,
                    itemId,
                    workspace.getId());
        });
        return results.saveAndFlush(Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .reviewId(reviewId)
                .practiceId(Objects.requireNonNull(practiceId))
                .practiceVersionId(versionId)
                .itemId(itemId)
                .itemVersion("v1")
                .developerId(developerId)
                .outcome(ResultOutcome.MET)
                .coverage(JsonNodeFactory.instance
                        .objectNode()
                        .put("source", "claimed")
                        .put("complete", true)
                        .put("selected", 1)
                        .put("checked", 1)
                        .put("total", 1))
                .evidence(JsonNodeFactory.instance
                        .objectNode()
                        .set("citations", JsonNodeFactory.instance.arrayNode())
                        .set("openQuestions", JsonNodeFactory.instance.arrayNode()))
                .evidenceVersion((short) 1)
                .recordedAt(AT)
                .build());
    }

    protected void bind(Feedback parent, Result result) {
        basis.saveAndFlush(FeedbackBasis.builder()
                .id(FeedbackIds.basis(parent.getId(), result.getId(), null))
                .workspaceId(workspace.getId())
                .feedbackId(parent.getId())
                .resultId(result.getId())
                .developerId(developerId)
                .ordinal(0)
                .build());
    }
}

package de.tum.cit.aet.hephaestus.practices.feedback;

import static de.tum.cit.aet.hephaestus.practices.result.ResultTestFixtures.resultBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.PracticePageSupportReader;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackBasisRepositoryIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    private FeedbackQueryRepository queries;

    @Autowired
    private PracticePageSupportReader support;

    @Autowired
    private DeliveredCommentLookupService delivered;

    @Test
    void shouldReadOnlyThisWorkspacesBasisInItsStoredOrder() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId(), parent.getId()))
                .extracting(FeedbackBasis::getResultId)
                .containsExactly(result.getId());
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId() + 1, parent.getId()))
                .isEmpty();
    }

    @Test
    void shouldKeepResultsAfterFeedbackIsDeleted() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        feedback.deleteById(parent.getId());
        feedback.flush();
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId(), parent.getId()))
                .isEmpty();
        assertThat(results.findById(result.getId())).isPresent();
    }

    @Test
    void shouldReadPinnedOriginsInOneWorkspaceForTheSelectedReviews() {
        var backfill = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)"
                        + " SELECT"
                        + " ?,workspace_id,item_id,kind,item_version,'BACKFILL',status,ordered_at,created_at"
                        + " FROM review WHERE workspace_id=? AND id=?",
                backfill,
                workspace.getId(),
                reviewId);
        assertThat(queries.reviewOrigins(workspace.getId(), List.of(reviewId, backfill)))
                .containsExactlyInAnyOrder(ReviewOrigin.REQUEST, ReviewOrigin.BACKFILL);
        assertThat(queries.reviewOrigins(workspace.getId() + 1, List.of(reviewId, backfill)))
                .isEmpty();
    }

    @Test
    void shouldSelectThePracticeSupportWhenMoreThanOneHundredOtherResultsAreNewer() {
        var expected = negativeResult();
        var otherPractice = result();
        for (int position = 1; position <= 100; position++) {
            currentResultOnNewWork(otherPractice, AT.plusSeconds(position), position);
        }
        var samePracticeMet = resultBuilder(otherPractice)
                .practiceId(expected.getPracticeId())
                .practiceVersionId(expected.getPracticeVersionId())
                .build();
        currentResultOnNewWork(samePracticeMet, AT.plusSeconds(101), 101);
        assertThat(support.select(workspace.getId(), developerId, expected.getPracticeId(), AT.plusSeconds(102))
                        .evidence())
                .extracting(Result::getId)
                .containsExactly(expected.getId());
        assertThatThrownBy(() -> support.select(
                        workspace.getId() + 1, developerId, expected.getPracticeId(), AT.plusSeconds(102)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldUseTheSuppliedTimeForThePracticeSupportLookback() {
        var expected = negativeResult();
        assertThat(support.select(
                                workspace.getId(), developerId, expected.getPracticeId(), AT.plus(Duration.ofDays(90)))
                        .evidence())
                .isEmpty();
        assertThat(support.select(workspace.getId(), developerId, expected.getPracticeId(), AT.plus(Duration.ofDays(1)))
                        .evidence())
                .extracting(Result::getId)
                .containsExactly(expected.getId());
    }

    @Test
    void shouldRequireTheRequestedWorkspacesNativeRepositoryBeforeReturningReceipts() {
        long repository = 900;
        long parent = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO item(workspace_id,kind,source_id,created_at) VALUES"
                        + " (?,'scm.repository',?,?) RETURNING id",
                Long.class,
                workspace.getId(),
                repository,
                Timestamp.from(AT)));
        long work = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO item(workspace_id,kind,source_id,parent_id,created_at)"
                        + " VALUES (?,'scm.pull_request',43,?,?) RETURNING id",
                Long.class,
                workspace.getId(),
                parent,
                Timestamp.from(AT)));
        var review = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)"
                        + " SELECT"
                        + " ?,workspace_id,?,kind,item_version,origin,status,ordered_at,created_at FROM"
                        + " review WHERE workspace_id=? AND id=?",
                review,
                work,
                workspace.getId(),
                reviewId);
        var piece = feedback.saveAndFlush(Feedback.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .reviewId(review)
                .itemId(work)
                .developerId(developerId)
                .destination(FeedbackDestination.ON_WORK)
                .state(FeedbackState.DELIVERED)
                .body("Add a focused regression test.")
                .createdAt(AT)
                .deliveredAt(AT)
                .build());
        var copy = summary(piece, FeedbackCopyState.POSTED);
        var kind = new ItemKind("scm.pull_request");
        assertThat(delivered
                        .findForRepository(workspace.getId(), repository, kind)
                        .get(43L))
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.externalRef()).isEqualTo(copy.getExternalRef()));
        assertThat(delivered.findForRepository(workspace.getId() + 1, repository, kind))
                .isEmpty();
        assertThat(delivered.findForRepository(workspace.getId(), repository + 1, kind))
                .isEmpty();
        assertThat(delivered.findForRepository(workspace.getId(), repository, new ItemKind("scm.issue")))
                .isEmpty();
    }

    private void currentResultOnNewWork(Result template, Instant recordedAt, int sourceOffset) {
        long nextItem = Objects.requireNonNull(jdbc.queryForObject(
                "INSERT INTO item(workspace_id,kind,source_id,created_at) VALUES"
                        + " (?,'scm.pull_request',?,?) RETURNING id",
                Long.class,
                workspace.getId(),
                1000L + sourceOffset,
                Timestamp.from(AT)));
        var nextReview = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)"
                        + " SELECT"
                        + " ?,workspace_id,?,'ITEM',item_version,origin,status,ordered_at,created_at"
                        + " FROM review WHERE workspace_id=? AND id=?",
                nextReview,
                nextItem,
                workspace.getId(),
                template.getReviewId());
        jdbc.update(
                "INSERT INTO"
                        + " review_practice(review_id,practice_id,workspace_id,practice_version_id,status,method)"
                        + " SELECT ?,practice_id,workspace_id,practice_version_id,status,method FROM"
                        + " review_practice WHERE workspace_id=? AND review_id=? AND practice_id=?",
                nextReview,
                workspace.getId(),
                template.getReviewId(),
                template.getPracticeId());
        jdbc.update(
                "INSERT INTO"
                        + " review_item(review_id,practice_id,item_id,workspace_id,item_version,state)"
                        + " VALUES (?,?,?,?,'v1','LISTED')",
                nextReview,
                template.getPracticeId(),
                nextItem,
                workspace.getId());
        results.saveAndFlush(resultBuilder(template)
                .id(UUID.randomUUID())
                .itemId(nextItem)
                .reviewId(nextReview)
                .recordedAt(recordedAt)
                .build());
    }
}

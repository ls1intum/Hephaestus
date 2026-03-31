package de.tum.in.www1.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PracticeReviewDeliveryGate}.
 * <p>
 * The gate is a pure decision function (no repository dependencies beyond
 * {@link PracticeReviewProperties}), so tests need no mocks — just construct
 * inputs and assert the decision variant.
 */
class PracticeReviewDeliveryGateTest extends BaseUnitTest {

    private static final Long PR_ID = 42L;

    private PracticeReviewDeliveryGate gate;

    @BeforeEach
    void setUp() {
        PracticeReviewProperties properties = new PracticeReviewProperties(false, true, false, 5, "");
        gate = new PracticeReviewDeliveryGate(properties);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PullRequest createOpenPr() {
        PullRequest pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setState(Issue.State.OPEN);
        User author = new User();
        author.setId(1L);
        pr.setAuthor(author);
        return pr;
    }

    // ── Gate Check Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("7. Delivery Content Gate (negative findings, no delivery)")
    class DeliveryContentGateTests {

        @Test
        @DisplayName("Should STORE_ONLY when no delivery content for negative findings")
        void storeOnlyWhenNoDeliveryContent() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, false, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo(
                "no delivery content for negative findings"
            );
        }

        @Test
        @DisplayName("PR state checks take precedence over delivery content check")
        void prStateBeforeDeliveryContent() {
            // Null PR returns "PR not found" even when delivery content is also missing
            DeliveryDecision decision = gate.evaluate(null, true, false, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR not found");
        }
    }

    @Nested
    @DisplayName("2. PR State Gate")
    class PrStateGateTests {

        @Test
        @DisplayName("Should STORE_ONLY when PR is null (not found)")
        void storeOnlyWhenPrNull() {
            DeliveryDecision decision = gate.evaluate(null, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR not found");
        }

        @Test
        @DisplayName("Should STORE_ONLY when PR is CLOSED")
        void storeOnlyWhenPrClosed() {
            PullRequest pr = createOpenPr();
            pr.setState(Issue.State.CLOSED);

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is CLOSED");
        }

        @Test
        @DisplayName("Should STORE_ONLY when PR is MERGED")
        void storeOnlyWhenPrMerged() {
            PullRequest pr = createOpenPr();
            pr.setState(Issue.State.MERGED);

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is MERGED");
        }
    }

    @Nested
    @DisplayName("3. Draft Gate")
    class DraftGateTests {

        @Test
        @DisplayName("Should STORE_ONLY when PR is draft and skipDrafts=true")
        void storeOnlyWhenDraft() {
            PullRequest pr = createOpenPr();
            pr.setDraft(true);

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is draft");
        }

        @Test
        @DisplayName("Should continue when PR is draft but skipDrafts=false")
        void continueWhenSkipDraftsDisabled() {
            PracticeReviewProperties noSkipProps = new PracticeReviewProperties(false, false, false, 5, "");
            PracticeReviewDeliveryGate noSkipGate = new PracticeReviewDeliveryGate(noSkipProps);

            PullRequest pr = createOpenPr();
            pr.setDraft(true);

            // Should reach the PostNew decision (has negatives, no previous)
            DeliveryDecision decision = noSkipGate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.PostNew.class);
        }
    }

    @Nested
    @DisplayName("4. User Preference Gate")
    class UserPreferenceGateTests {

        @Test
        @DisplayName("Should STORE_ONLY when user opted out of AI review")
        void storeOnlyWhenOptedOut() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, true, false, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("user opted out");
        }

        @Test
        @DisplayName("Should continue when user has AI review enabled")
        void continueWhenEnabled() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.PostNew.class);
        }
    }

    @Nested
    @DisplayName("5. All Findings Positive")
    class AllPositiveTests {

        @Test
        @DisplayName("Should STORE_ONLY when all positive and no previous comment")
        void storeOnlyWhenAllPositiveFirstAnalysis() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, false, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("all findings positive");
        }
    }

    @Nested
    @DisplayName("6. All Resolved (re-analysis)")
    class AllResolvedTests {

        @Test
        @DisplayName("Should EDIT_ALL_RESOLVED when all positive and previous comment exists")
        void editAllResolvedWhenReAnalysisAllPositive() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, false, true, true, "IC_previous123");

            assertThat(decision).isInstanceOf(DeliveryDecision.EditAllResolved.class);
            DeliveryDecision.EditAllResolved resolved = (DeliveryDecision.EditAllResolved) decision;
            assertThat(resolved.pullRequest()).isSameAs(pr);
            assertThat(resolved.commentId()).isEqualTo("IC_previous123");
        }

        @Test
        @DisplayName("Should EDIT_ALL_RESOLVED even when agent omits delivery content (all-positive)")
        void editAllResolvedEvenWithoutDeliveryContent() {
            PullRequest pr = createOpenPr();

            // Agent prompt says: omit delivery when all positive → hasDeliveryContent=false
            // The gate must still reach EDIT_ALL_RESOLVED for re-analysis
            DeliveryDecision decision = gate.evaluate(pr, false, false, true, "IC_previous123");

            assertThat(decision).isInstanceOf(DeliveryDecision.EditAllResolved.class);
            DeliveryDecision.EditAllResolved resolved = (DeliveryDecision.EditAllResolved) decision;
            assertThat(resolved.commentId()).isEqualTo("IC_previous123");
        }
    }

    @Nested
    @DisplayName("7. Re-analysis with negatives")
    class ReAnalysisTests {

        @Test
        @DisplayName("Should EDIT_EXISTING when has negatives and previous comment exists")
        void editExistingOnReAnalysis() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, "IC_previous123");

            assertThat(decision).isInstanceOf(DeliveryDecision.EditExisting.class);
            DeliveryDecision.EditExisting edit = (DeliveryDecision.EditExisting) decision;
            assertThat(edit.pullRequest()).isSameAs(pr);
            assertThat(edit.commentId()).isEqualTo("IC_previous123");
        }
    }

    @Nested
    @DisplayName("8. First Analysis (happy path)")
    class FirstAnalysisTests {

        @Test
        @DisplayName("Should POST_NEW when has negatives and no previous comment")
        void postNewOnFirstAnalysis() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.PostNew.class);
            DeliveryDecision.PostNew postNew = (DeliveryDecision.PostNew) decision;
            assertThat(postNew.pullRequest()).isSameAs(pr);
        }
    }

    @Nested
    @DisplayName("Ordering Guarantees")
    class OrderingTests {

        @Test
        @DisplayName("PR state takes precedence over delivery content check")
        void prStateBeforeDeliveryContent() {
            PullRequest pr = createOpenPr();
            pr.setState(Issue.State.CLOSED);

            // Closed PR should win over missing delivery content
            DeliveryDecision decision = gate.evaluate(pr, true, false, true, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is CLOSED");
        }

        @Test
        @DisplayName("PR state check takes precedence over user preference")
        void prStateBeforeUserPreference() {
            PullRequest pr = createOpenPr();
            pr.setState(Issue.State.MERGED);

            // Even with opted-out user, PR state should win
            DeliveryDecision decision = gate.evaluate(pr, true, true, false, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is MERGED");
        }

        @Test
        @DisplayName("Draft check takes precedence over user preference")
        void draftBeforeUserPreference() {
            PullRequest pr = createOpenPr();
            pr.setDraft(true);

            DeliveryDecision decision = gate.evaluate(pr, true, true, false, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("PR is draft");
        }

        @Test
        @DisplayName("User preference takes precedence over all-positive check")
        void userPreferenceBeforeAllPositive() {
            PullRequest pr = createOpenPr();

            // All positive findings but user opted out — should be "user opted out"
            DeliveryDecision decision = gate.evaluate(pr, false, true, false, null);

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("user opted out");
        }

        @Test
        @DisplayName("User opt-out takes precedence over all-resolved re-analysis")
        void userOptOutBeforeAllResolved() {
            PullRequest pr = createOpenPr();

            // All positive with previous comment, but user opted out — should be "user opted out"
            DeliveryDecision decision = gate.evaluate(pr, false, true, false, "IC_previous123");

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("user opted out");
        }
    }

    @Nested
    @DisplayName("Blank Comment ID Normalization")
    class BlankCommentIdTests {

        @Test
        @DisplayName("Blank previousCommentId treated as null (first analysis, not edit)")
        void blankCommentIdTreatedAsNull() {
            PullRequest pr = createOpenPr();

            // Empty string should be normalized to null → POST_NEW, not EDIT_EXISTING
            DeliveryDecision decision = gate.evaluate(pr, true, true, true, "");

            assertThat(decision).isInstanceOf(DeliveryDecision.PostNew.class);
        }

        @Test
        @DisplayName("Whitespace-only previousCommentId treated as null")
        void whitespaceCommentIdTreatedAsNull() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, true, true, true, "   ");

            assertThat(decision).isInstanceOf(DeliveryDecision.PostNew.class);
        }

        @Test
        @DisplayName("Blank previousCommentId with all-positive → STORE_ONLY (not EDIT_ALL_RESOLVED)")
        void blankCommentIdAllPositiveIsStoreOnly() {
            PullRequest pr = createOpenPr();

            DeliveryDecision decision = gate.evaluate(pr, false, true, true, "");

            assertThat(decision).isInstanceOf(DeliveryDecision.StoreOnly.class);
            assertThat(((DeliveryDecision.StoreOnly) decision).reason()).isEqualTo("all findings positive");
        }
    }

    @Nested
    @DisplayName("DeliveryDecision records")
    class RecordValidationTests {

        @Test
        @DisplayName("StoreOnly should throw on null reason")
        void storeOnlyThrowsOnNullReason() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.StoreOnly(null)
            );
        }

        @Test
        @DisplayName("PostNew should throw on null pullRequest")
        void postNewThrowsOnNullPr() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.PostNew(null)
            );
        }

        @Test
        @DisplayName("EditExisting should throw on null pullRequest")
        void editExistingThrowsOnNullPr() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.EditExisting(null, "IC_123")
            );
        }

        @Test
        @DisplayName("EditExisting should throw on null commentId")
        void editExistingThrowsOnNullCommentId() {
            PullRequest pr = createOpenPr();
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.EditExisting(pr, null)
            );
        }

        @Test
        @DisplayName("EditAllResolved should throw on null pullRequest")
        void editAllResolvedThrowsOnNullPr() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.EditAllResolved(null, "IC_123")
            );
        }

        @Test
        @DisplayName("EditAllResolved should throw on null commentId")
        void editAllResolvedThrowsOnNullCommentId() {
            PullRequest pr = createOpenPr();
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new DeliveryDecision.EditAllResolved(pr, null)
            );
        }
    }
}

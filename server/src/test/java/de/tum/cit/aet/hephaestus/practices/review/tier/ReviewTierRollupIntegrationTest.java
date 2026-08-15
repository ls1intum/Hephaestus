package de.tum.cit.aet.hephaestus.practices.review.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.dto.AreaReviewTierRollupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReviewTierRollupDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@code GET /workspaces/{slug}/practices/review-tiers} — the read the practice management screen is
 * built on. {@link ReviewTierResolverTest} pins the resolution chain as a function; this pins what the
 * endpoint does with it over real rows, where the counts are resolved in the JVM rather than in SQL.
 */
@DisplayName("Review tier rollup")
class ReviewTierRollupIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String URI = "/workspaces/{workspaceSlug}/practices/review-tiers";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeAreaRepository areaRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("rollup-owner");
        workspace = createWorkspace("rollup-ws", "Rollup WS", "rollup-org", AccountType.ORG, owner);
    }

    private PracticeArea persistArea(String slug, int displayOrder, @Nullable PracticeReviewTier tier) {
        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName("Area " + slug);
        area.setDisplayOrder(displayOrder);
        area.setReviewTier(tier);
        return areaRepository.save(area);
    }

    private Practice persistPractice(String slug, @Nullable PracticeArea area, @Nullable PracticeReviewTier tier) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName("Practice " + slug);
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        practice.setCriteria("Detect prompt for " + slug);
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        practice.setArea(area);
        practice.setReviewTier(tier);
        return practiceRepository.save(practice);
    }

    private void workspaceDefaultsTo(@Nullable PracticeReviewTier tier, @Nullable FeedbackReach reach) {
        Workspace stored = workspaceRepository.findById(workspace.getId()).orElseThrow();
        stored.getReviewSettings().applyDefaultReviewTier(tier == null ? null : tier.name());
        stored.getReviewSettings().applyFeedbackReach(reach == null ? null : reach.name());
        workspaceRepository.save(stored);
    }

    private ReviewTierRollupDTO fetchRollup() {
        ReviewTierRollupDTO rollup = webTestClient
            .get()
            .uri(URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ReviewTierRollupDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(rollup).isNotNull();
        return rollup;
    }

    private static AreaReviewTierRollupDTO areaNamed(ReviewTierRollupDTO rollup, @Nullable String slug) {
        return rollup
            .areas()
            .stream()
            .filter(area -> Objects.equals(area.areaSlug(), slug))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no area group for slug " + slug + " in " + rollup.areas()));
    }

    /**
     * One workspace covering every branch of the chain at once, so a single fetch can be asserted from
     * several angles without each test rebuilding a catalogue. The workspace default is deliberately
     * {@code PROPOSE} rather than {@link PracticeReviewTier#DEFAULT}, so a rollup that silently fell back
     * to the constant instead of reading the workspace level would be caught.
     */
    @Nested
    @DisplayName("over a catalogue that uses every level of the chain")
    class OverAMixedCatalogue {

        @BeforeEach
        void seedCatalogue() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeReviewTier.PROPOSE, FeedbackReach.CONVERSATION);

            PracticeArea alpha = persistArea("alpha", 0, PracticeReviewTier.OFF);
            persistPractice("alpha-inherits", alpha, null);
            persistPractice("alpha-decides", alpha, PracticeReviewTier.DELIVER);

            PracticeArea beta = persistArea("beta", 1, null);
            persistPractice("beta-inherits", beta, null);

            // An area nobody has filed anything under yet.
            persistArea("gamma", 2, null);

            // No area at all: skips the middle level entirely.
            persistPractice("unfiled", null, null);
        }

        @Test
        @WithAdminUser
        @DisplayName("every tier is a key, even the ones at zero")
        void everyTierIsAKeyEvenAtZero() {
            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnlyKeys(PracticeReviewTier.values());
            assertThat(rollup.counts()).containsEntry(PracticeReviewTier.OFF, 1);
            assertThat(rollup.areas())
                .isNotEmpty()
                .allSatisfy(area -> assertThat(area.counts()).containsOnlyKeys(PracticeReviewTier.values()));
        }

        @Test
        @WithAdminUser
        @DisplayName("counts the effective tier after the chain, not the stored override")
        void countsTheEffectiveTierNotTheStoredOverride() {
            long practicesHoldingATierOfTheirOwn = practiceRepository
                .findAllForCatalog(workspace.getId())
                .stream()
                .filter(practice -> practice.getReviewTier() != null)
                .count();
            assertThat(practicesHoldingATierOfTheirOwn).isEqualTo(1);

            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnly(
                entry(PracticeReviewTier.OFF, 1), // alpha-inherits, from its area
                entry(PracticeReviewTier.PROPOSE, 2), // beta-inherits and unfiled, from the workspace
                entry(PracticeReviewTier.DELIVER, 1) // alpha-decides, from itself
            );
            assertThat(rollup.counts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
        }

        @Test
        @WithAdminUser
        @DisplayName("an area counts its own practices at their effective tier")
        void areaCountsFollowTheSameChain() {
            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(areaNamed(rollup, "alpha").counts()).containsOnly(
                entry(PracticeReviewTier.OFF, 1),
                entry(PracticeReviewTier.PROPOSE, 0),
                entry(PracticeReviewTier.DELIVER, 1)
            );
            assertThat(areaNamed(rollup, "beta").counts()).containsEntry(PracticeReviewTier.PROPOSE, 1);
        }

        /**
         * {@code overriddenCount} counts only the practice's own stored tier: {@code alpha} area holds a
         * tier itself, but that makes the area the decider for the practices under it, not a practice that
         * overrode the area.
         */
        @Test
        @WithAdminUser
        @DisplayName("overriddenCount counts only practices that decided for themselves")
        void overriddenCountCountsSelfDecidedPracticesOnly() {
            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(areaNamed(rollup, "alpha").overriddenCount()).isEqualTo(1);
            assertThat(areaNamed(rollup, "beta").overriddenCount()).isZero();
            assertThat(areaNamed(rollup, "gamma").overriddenCount()).isZero();
            assertThat(areaNamed(rollup, null).overriddenCount()).isZero();
        }

        @Test
        @WithAdminUser
        @DisplayName("an area with no practices is reported at all zeroes rather than omitted")
        void anEmptyAreaIsStillReported() {
            ReviewTierRollupDTO rollup = fetchRollup();

            AreaReviewTierRollupDTO gamma = areaNamed(rollup, "gamma");
            assertThat(gamma.counts().values()).containsOnly(0);
            assertThat(gamma.reviewTier().effective()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(gamma.reviewTier().source()).isEqualTo(ReviewTierSource.WORKSPACE);
        }

        /** No row exists to set a tier on the no-area group, so it always reports the workspace as the source. */
        @Test
        @WithAdminUser
        @DisplayName("the no-area group sorts last, is named by two nulls, and can hold no decision")
        void theNoAreaGroupSortsLastAndHoldsNoDecision() {
            ReviewTierRollupDTO rollup = fetchRollup();

            AreaReviewTierRollupDTO ungrouped = rollup.areas().getLast();
            assertThat(ungrouped.areaSlug()).isNull();
            assertThat(ungrouped.areaName()).isNull();
            assertThat(rollup.areas().stream().map(AreaReviewTierRollupDTO::areaSlug)).containsExactly(
                "alpha",
                "beta",
                "gamma",
                null
            );

            assertThat(ungrouped.reviewTier().override()).isNull();
            assertThat(ungrouped.reviewTier().inherited()).isTrue();
            assertThat(ungrouped.reviewTier().source()).isEqualTo(ReviewTierSource.WORKSPACE);
            assertThat(ungrouped.reviewTier().effective()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(ungrouped.counts()).containsEntry(PracticeReviewTier.PROPOSE, 1);
        }

        @Test
        @WithAdminUser
        @DisplayName("each area reports whether the tier is its own or the workspace's")
        void eachAreaReportsWhoDecided() {
            ReviewTierRollupDTO rollup = fetchRollup();

            AreaReviewTierRollupDTO alpha = areaNamed(rollup, "alpha");
            assertThat(alpha.reviewTier().effective()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(alpha.reviewTier().override()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(alpha.reviewTier().source()).isEqualTo(ReviewTierSource.AREA);
            assertThat(alpha.reviewTier().inherited()).isFalse();

            AreaReviewTierRollupDTO beta = areaNamed(rollup, "beta");
            assertThat(beta.reviewTier().effective()).isEqualTo(PracticeReviewTier.PROPOSE);
            assertThat(beta.reviewTier().override()).isNull();
            assertThat(beta.reviewTier().source()).isEqualTo(ReviewTierSource.WORKSPACE);
            assertThat(beta.reviewTier().inherited()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("carries the workspace's feedback reach alongside the counts")
        void carriesTheWorkspacesReach() {
            assertThat(fetchRollup().feedbackReach()).isEqualTo(FeedbackReach.CONVERSATION);
        }

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member")
        void shouldReturn403ForNonAdmin() {
            User member = persistUser("mentor");
            ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);

            webTestClient
                .get()
                .uri(URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient.get().uri(URI, workspace.getWorkspaceSlug()).exchange().expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("the workspace level itself")
    class TheWorkspaceLevel {

        @Test
        @WithAdminUser
        @DisplayName("a workspace that never chose reports the vocabulary's default as an inheritance")
        void anUnsetWorkspaceReportsAnInheritance() {
            ensureAdminMembership(workspace);
            persistPractice("solo", null, null);

            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(rollup.workspaceDefault().effective()).isEqualTo(PracticeReviewTier.DELIVER);
            assertThat(rollup.workspaceDefault().override()).isNull();
            assertThat(rollup.workspaceDefault().inherited()).isTrue();
            assertThat(rollup.workspaceDefault().source()).isEqualTo(ReviewTierSource.WORKSPACE);
            assertThat(rollup.feedbackReach()).isEqualTo(FeedbackReach.DEFAULT);
            assertThat(rollup.counts()).containsEntry(PracticeReviewTier.DELIVER, 1);
        }

        /** The raw column is reported alongside the resolved value so a caller can tell "chose OFF" from "inherited". */
        @Test
        @WithAdminUser
        @DisplayName("a workspace that chose reports its own decision, and every unopinionated practice moves")
        void aWorkspaceThatChoseIsNotInheriting() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeReviewTier.OFF, null);
            persistPractice("solo", null, null);

            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(rollup.workspaceDefault().effective()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(rollup.workspaceDefault().override()).isEqualTo(PracticeReviewTier.OFF);
            assertThat(rollup.workspaceDefault().inherited()).isFalse();
            assertThat(rollup.counts()).containsEntry(PracticeReviewTier.OFF, 1);
            assertThat(rollup.counts()).containsEntry(PracticeReviewTier.DELIVER, 0);
        }

        @Test
        @WithAdminUser
        @DisplayName("an empty catalogue answers with every tier at zero and no groups")
        void anEmptyCatalogueStillAnswers() {
            ensureAdminMembership(workspace);

            ReviewTierRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnlyKeys(PracticeReviewTier.values());
            assertThat(rollup.counts().values()).containsOnly(0);
            assertThat(rollup.areas()).isEmpty();
        }
    }

    /** A leak from another workspace would be invisible in the totals until reconciled by hand. */
    @Test
    @WithAdminUser
    @DisplayName("counts only this workspace's practices")
    void doesNotCountAnotherWorkspacesPractices() {
        ensureAdminMembership(workspace);
        persistPractice("ours", null, null);

        User otherOwner = persistUser("other-rollup-owner");
        Workspace other = createWorkspace("other-rollup-ws", "Other", "other-rollup-org", AccountType.ORG, otherOwner);
        Practice theirs = new Practice();
        theirs.setWorkspace(other);
        theirs.setSlug("theirs");
        theirs.setName("Theirs");
        theirs.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        theirs.setCriteria("Detect prompt for theirs");
        theirs.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        theirs.setReviewTier(PracticeReviewTier.OFF);
        practiceRepository.save(theirs);

        ReviewTierRollupDTO rollup = fetchRollup();

        assertThat(rollup.counts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(rollup.counts()).containsEntry(PracticeReviewTier.DELIVER, 1);
        assertThat(rollup.counts()).containsEntry(PracticeReviewTier.OFF, 0);
    }
}

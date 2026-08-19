package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.dto.AreaAutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.AutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
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

@DisplayName("Review autonomy rollup")
class AutonomyRollupIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String URI = "/workspaces/{workspaceSlug}/practices/autonomy";

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

    private PracticeArea persistArea(String slug, int displayOrder, @Nullable PracticeAutonomy autonomy) {
        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName("Area " + slug);
        area.setDisplayOrder(displayOrder);
        area.setAutonomy(autonomy);
        return areaRepository.save(area);
    }

    private Practice persistPractice(String slug, @Nullable PracticeArea area, @Nullable PracticeAutonomy autonomy) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName("Practice " + slug);
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        practice.setCriteria("Detect prompt for " + slug);
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        practice.setArea(area);
        practice.setAutonomy(autonomy);
        return practiceRepository.save(practice);
    }

    private void workspaceDefaultsTo(@Nullable PracticeAutonomy autonomy) {
        Workspace stored = workspaceRepository.findById(workspace.getId()).orElseThrow();
        stored.getReviewSettings().applyDefaultAutonomy(autonomy == null ? null : autonomy.name());
        workspaceRepository.save(stored);
    }

    private AutonomyRollupDTO fetchRollup() {
        AutonomyRollupDTO rollup = webTestClient
            .get()
            .uri(URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AutonomyRollupDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(rollup).isNotNull();
        return rollup;
    }

    private static AreaAutonomyRollupDTO areaNamed(AutonomyRollupDTO rollup, @Nullable String slug) {
        return rollup
            .areas()
            .stream()
            .filter(area -> Objects.equals(area.areaSlug(), slug))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no area group for slug " + slug + " in " + rollup.areas()));
    }

    @Nested
    @DisplayName("over a catalogue that uses every level of the chain")
    class OverAMixedCatalogue {

        @BeforeEach
        void seedCatalogue() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeAutonomy.HUMAN_APPROVAL);

            PracticeArea alpha = persistArea("alpha", 0, PracticeAutonomy.OFF);
            persistPractice("alpha-inherits", alpha, null);
            persistPractice("alpha-decides", alpha, PracticeAutonomy.AUTOMATIC);

            PracticeArea beta = persistArea("beta", 1, null);
            persistPractice("beta-inherits", beta, null);

            persistArea("gamma", 2, null);

            persistPractice("unfiled", null, null);
        }

        @Test
        @WithAdminUser
        @DisplayName("every autonomy is a key, even the ones at zero")
        void everyTierIsAKeyEvenAtZero() {
            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnlyKeys(PracticeAutonomy.values());
            assertThat(rollup.counts()).containsEntry(PracticeAutonomy.OFF, 1);
            assertThat(rollup.areas())
                .isNotEmpty()
                .allSatisfy(area -> assertThat(area.counts()).containsOnlyKeys(PracticeAutonomy.values()));
        }

        @Test
        @WithAdminUser
        @DisplayName("counts the effective autonomy after the chain, not the stored override")
        void countsTheEffectiveTierNotTheStoredOverride() {
            long practicesHoldingATierOfTheirOwn = practiceRepository
                .findAllForCatalog(workspace.getId())
                .stream()
                .filter(practice -> practice.getAutonomy() != null)
                .count();
            assertThat(practicesHoldingATierOfTheirOwn).isEqualTo(1);

            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnly(
                entry(PracticeAutonomy.OFF, 1),
                entry(PracticeAutonomy.HUMAN_APPROVAL, 2),
                entry(PracticeAutonomy.AUTOMATIC, 1)
            );
            assertThat(rollup.counts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
        }

        @Test
        @WithAdminUser
        @DisplayName("an area counts its own practices at their effective autonomy")
        void areaCountsFollowTheSameChain() {
            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(areaNamed(rollup, "alpha").counts()).containsOnly(
                entry(PracticeAutonomy.OFF, 1),
                entry(PracticeAutonomy.HUMAN_APPROVAL, 0),
                entry(PracticeAutonomy.AUTOMATIC, 1)
            );
            assertThat(areaNamed(rollup, "beta").counts()).containsEntry(PracticeAutonomy.HUMAN_APPROVAL, 1);
        }

        @Test
        @WithAdminUser
        @DisplayName("overriddenCount counts only practices that decided for themselves")
        void overriddenCountCountsSelfDecidedPracticesOnly() {
            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(areaNamed(rollup, "alpha").overriddenCount()).isEqualTo(1);
            assertThat(areaNamed(rollup, "beta").overriddenCount()).isZero();
            assertThat(areaNamed(rollup, "gamma").overriddenCount()).isZero();
            assertThat(areaNamed(rollup, null).overriddenCount()).isZero();
        }

        @Test
        @WithAdminUser
        @DisplayName("an area with no practices is reported at all zeroes rather than omitted")
        void anEmptyAreaIsStillReported() {
            AutonomyRollupDTO rollup = fetchRollup();

            AreaAutonomyRollupDTO gamma = areaNamed(rollup, "gamma");
            assertThat(gamma.counts().values()).containsOnly(0);
            assertThat(gamma.autonomy().effective()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(gamma.autonomy().source()).isEqualTo(AutonomySource.WORKSPACE);
        }

        @Test
        @WithAdminUser
        @DisplayName("the no-area group sorts last, is named by two nulls, and can hold no decision")
        void theNoAreaGroupSortsLastAndHoldsNoDecision() {
            AutonomyRollupDTO rollup = fetchRollup();

            AreaAutonomyRollupDTO ungrouped = rollup.areas().getLast();
            assertThat(ungrouped.areaSlug()).isNull();
            assertThat(ungrouped.areaName()).isNull();
            assertThat(rollup.areas().stream().map(AreaAutonomyRollupDTO::areaSlug)).containsExactly(
                "alpha",
                "beta",
                "gamma",
                null
            );

            assertThat(ungrouped.autonomy().override()).isNull();
            assertThat(ungrouped.autonomy().inherited()).isTrue();
            assertThat(ungrouped.autonomy().source()).isEqualTo(AutonomySource.WORKSPACE);
            assertThat(ungrouped.autonomy().effective()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(ungrouped.counts()).containsEntry(PracticeAutonomy.HUMAN_APPROVAL, 1);
        }

        @Test
        @WithAdminUser
        @DisplayName("each area reports whether the autonomy is its own or the workspace's")
        void eachAreaReportsWhoDecided() {
            AutonomyRollupDTO rollup = fetchRollup();

            AreaAutonomyRollupDTO alpha = areaNamed(rollup, "alpha");
            assertThat(alpha.autonomy().effective()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(alpha.autonomy().override()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(alpha.autonomy().source()).isEqualTo(AutonomySource.AREA);
            assertThat(alpha.autonomy().inherited()).isFalse();

            AreaAutonomyRollupDTO beta = areaNamed(rollup, "beta");
            assertThat(beta.autonomy().effective()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(beta.autonomy().override()).isNull();
            assertThat(beta.autonomy().source()).isEqualTo(AutonomySource.WORKSPACE);
            assertThat(beta.autonomy().inherited()).isTrue();
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

            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(rollup.workspaceDefault().effective()).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
            assertThat(rollup.workspaceDefault().override()).isNull();
            assertThat(rollup.workspaceDefault().inherited()).isTrue();
            assertThat(rollup.workspaceDefault().source()).isEqualTo(AutonomySource.WORKSPACE);
            assertThat(rollup.counts()).containsEntry(PracticeAutonomy.HUMAN_APPROVAL, 1);
        }

        @Test
        @WithAdminUser
        @DisplayName("a workspace that chose reports its own decision, and every unopinionated practice moves")
        void aWorkspaceThatChoseIsNotInheriting() {
            ensureAdminMembership(workspace);
            workspaceDefaultsTo(PracticeAutonomy.OFF);
            persistPractice("solo", null, null);

            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(rollup.workspaceDefault().effective()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(rollup.workspaceDefault().override()).isEqualTo(PracticeAutonomy.OFF);
            assertThat(rollup.workspaceDefault().inherited()).isFalse();
            assertThat(rollup.counts()).containsEntry(PracticeAutonomy.OFF, 1);
            assertThat(rollup.counts()).containsEntry(PracticeAutonomy.AUTOMATIC, 0);
        }

        @Test
        @WithAdminUser
        @DisplayName("an empty catalogue answers with every autonomy at zero and no groups")
        void anEmptyCatalogueStillAnswers() {
            ensureAdminMembership(workspace);

            AutonomyRollupDTO rollup = fetchRollup();

            assertThat(rollup.counts()).containsOnlyKeys(PracticeAutonomy.values());
            assertThat(rollup.counts().values()).containsOnly(0);
            assertThat(rollup.areas()).isEmpty();
        }
    }

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
        theirs.setAutonomy(PracticeAutonomy.OFF);
        practiceRepository.save(theirs);

        AutonomyRollupDTO rollup = fetchRollup();

        assertThat(rollup.counts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(rollup.counts()).containsEntry(PracticeAutonomy.HUMAN_APPROVAL, 1);
        assertThat(rollup.counts()).containsEntry(PracticeAutonomy.OFF, 0);
    }
}

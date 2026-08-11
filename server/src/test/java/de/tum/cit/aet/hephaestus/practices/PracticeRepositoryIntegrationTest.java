package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

class PracticeRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeAreaRepository practiceAreaRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("practice-test"));
    }

    private Practice createPractice(String slug, String name) {
        Practice practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Default criteria for " + slug);
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        return practice;
    }

    private PracticeArea persistArea(String slug, PracticeReviewTier reviewTier, int displayOrder) {
        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName("Area " + slug);
        area.setReviewTier(reviewTier);
        area.setDisplayOrder(displayOrder);
        return practiceAreaRepository.save(area);
    }

    @Nested
    class CrudTests {

        @Test
        void savesAndRetrieves() {
            Practice practice = createPractice("test-slug", "Test Practice");
            practice.setCriteria("Check for quality");
            practice.setReviewTier(PracticeReviewTier.OFF);

            Practice saved = practiceRepository.save(practice);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();

            Practice found = practiceRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.getSlug()).isEqualTo("test-slug");
            assertThat(found.getName()).isEqualTo("Test Practice");
            assertThat(found.getBindings()).isEqualTo(saved.getBindings());
            assertThat(found.getCriteria()).isEqualTo("Check for quality");
            assertThat(found.getReviewTier()).isEqualTo(PracticeReviewTier.OFF);
        }
    }

    @Nested
    class UniqueConstraintTests {

        @Test
        void rejectsDuplicateSlugInWorkspace() {
            practiceRepository.save(createPractice("unique-slug", "First"));

            Practice duplicate = createPractice("unique-slug", "Second");
            assertThatThrownBy(() -> practiceRepository.saveAndFlush(duplicate)).isInstanceOf(
                DataIntegrityViolationException.class
            );
        }

        @Test
        void allowsSameSlugInDifferentWorkspaces() {
            practiceRepository.save(createPractice("shared-slug", "First"));

            Workspace otherWorkspace = workspaceRepository.save(
                WorkspaceTestFixtures.activeWorkspace("other-workspace")
            );
            Practice otherPractice = createPractice("shared-slug", "Second");
            otherPractice.setWorkspace(otherWorkspace);

            Practice saved = practiceRepository.saveAndFlush(otherPractice);
            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    class QueryTests {

        /**
         * The gate reads the whole catalogue, at every tier, on purpose: it has to tell "nothing is bound
         * to this signal" apart from "something is bound and the workspace turned it off", which are
         * different answers to "why did nothing happen" and get different recorded reasons.
         */
        @Test
        void findsEveryPracticeOfTheWorkspaceWhateverItsTier() {
            Practice loud = createPractice("loud", "Loud");
            Practice silent = createPractice("silent", "Silent");
            silent.setReviewTier(PracticeReviewTier.OFF);
            practiceRepository.save(loud);
            practiceRepository.save(silent);

            List<Practice> result = practiceRepository.findByWorkspaceId(workspace.getId());

            assertThat(result).extracting(Practice::getSlug).containsExactlyInAnyOrder("loud", "silent");
        }

        /**
         * The work-type finder narrows by kind of work and by nothing else.
         *
         * <p>Its predecessor pushed {@code review_tier <> 'OFF'} into SQL, which was only ever correct
         * while the column could not be null: {@code NULL <> 'OFF'} is UNKNOWN, so the moment a practice
         * was allowed to hold no tier and inherit one, every inheriting practice would have vanished from
         * the reviewer's catalogue without a single row changing. The tier is resolved in the JVM instead,
         * so this query has to hand back the silenced and the inheriting ones alike.
         */
        @Test
        @DisplayName("findByWorkspaceIdAndArtifactKind returns every tier, including the inheriting NULL")
        void workTypeQueryReturnsEveryTierIncludingTheInheritingOnes() {
            Practice loud = createPractice("loud", "Loud");
            loud.setReviewTier(PracticeReviewTier.DELIVER);
            Practice observed = createPractice("observed", "Observed");
            observed.setReviewTier(PracticeReviewTier.PROPOSE);
            Practice silent = createPractice("silent", "Silent");
            silent.setReviewTier(PracticeReviewTier.OFF);
            // Holds no tier of its own: the row the deleted <> 'OFF' predicate silently dropped.
            Practice inheriting = createPractice("inheriting", "Inheriting");
            practiceRepository.saveAll(List.of(loud, observed, silent, inheriting));

            List<Practice> result = practiceRepository.findByWorkspaceIdAndArtifactKind(
                workspace.getId(),
                loud.getArtifactKind()
            );

            assertThat(result)
                .extracting(Practice::getSlug)
                .containsExactlyInAnyOrder("loud", "observed", "silent", "inheriting");
        }

        @Test
        @DisplayName("findByWorkspaceIdAndArtifactKind keeps out the other kinds of work")
        void workTypeQueryNarrowsToTheRequestedKind() {
            Practice pullRequest = createPractice("on-pull-requests", "On pull requests");
            Practice issue = createPractice("on-issues", "On issues");
            issue.setBindings(PracticeTestEvidence.bindings(ScmSignals.ISSUE_OPENED));
            issue.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.ISSUE));
            practiceRepository.saveAll(List.of(pullRequest, issue));

            List<Practice> result = practiceRepository.findByWorkspaceIdAndArtifactKind(
                workspace.getId(),
                ArtifactKinds.PULL_REQUEST
            );

            assertThat(result).extracting(Practice::getSlug).containsExactly("on-pull-requests");
        }

        /**
         * The tier rows are what the rollup and the "is anything switched on here" check read instead of
         * hydrating the catalogue. They carry both levels of the chain raw — a null means "this level
         * decided nothing", which the resolver needs in order to fall through to the next one, so a
         * projection that turned it into a value would resolve the chain wrongly and silently.
         */
        @Test
        @DisplayName("findReviewTierRows carries the practice's tier and its area's, nulls kept as nulls")
        void reviewTierRowsCarryBothLevelsAndKeepTheirNulls() {
            PracticeArea silencedArea = persistArea("silenced-area", PracticeReviewTier.OFF, 0);
            PracticeArea undecidedArea = persistArea("undecided-area", null, 1);
            Practice ownTier = createPractice("own-tier", "Own tier");
            ownTier.setReviewTier(PracticeReviewTier.PROPOSE);
            ownTier.setArea(silencedArea);
            Practice fromArea = createPractice("from-area", "From area");
            fromArea.setArea(silencedArea);
            Practice fromWorkspace = createPractice("from-workspace", "From workspace");
            fromWorkspace.setArea(undecidedArea);
            Practice unfiled = createPractice("unfiled", "Unfiled");
            practiceRepository.saveAll(List.of(ownTier, fromArea, fromWorkspace, unfiled));

            List<PracticeRepository.PracticeTierRow> rows = practiceRepository.findReviewTierRows(workspace.getId());

            assertThat(rows)
                .extracting(
                    PracticeRepository.PracticeTierRow::getPracticeTier,
                    PracticeRepository.PracticeTierRow::getAreaTier,
                    PracticeRepository.PracticeTierRow::getAreaId,
                    PracticeRepository.PracticeTierRow::getArtifactKind
                )
                .containsExactlyInAnyOrder(
                    tuple(
                        PracticeReviewTier.PROPOSE,
                        PracticeReviewTier.OFF,
                        silencedArea.getId(),
                        ArtifactKinds.PULL_REQUEST
                    ),
                    tuple(null, PracticeReviewTier.OFF, silencedArea.getId(), ArtifactKinds.PULL_REQUEST),
                    tuple(null, null, undecidedArea.getId(), ArtifactKinds.PULL_REQUEST),
                    // No area at all: the chain falls straight through to the workspace.
                    tuple(null, null, null, ArtifactKinds.PULL_REQUEST)
                );
        }

        @Test
        @DisplayName("findAllForCatalog returns the whole workspace catalogue, areas first")
        void catalogQueryReturnsEveryPracticeOrderedByArea() {
            PracticeArea first = persistArea("first-area", null, 0);
            PracticeArea second = persistArea("second-area", null, 1);
            Practice inSecond = createPractice("in-second", "In second");
            inSecond.setArea(second);
            Practice inFirst = createPractice("in-first", "In first");
            inFirst.setArea(first);
            // Holds no tier: findByFilters' `p.reviewTier = :reviewTier` could never have matched it.
            Practice unfiled = createPractice("unfiled", "Unfiled");
            practiceRepository.saveAll(List.of(inSecond, inFirst, unfiled));

            List<Practice> result = practiceRepository.findAllForCatalog(workspace.getId());

            assertThat(result).extracting(Practice::getSlug).containsExactly("in-first", "in-second", "unfiled");
        }

        @Test
        @DisplayName("findByWorkspaceIdAndSlug returns matching practice")
        void findsBySlug() {
            practiceRepository.save(createPractice("target", "Target"));
            practiceRepository.save(createPractice("other", "Other"));

            var found = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), "target");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Target");
        }

        @Test
        void existsByWorkspace() {
            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isFalse();

            practiceRepository.save(createPractice("test", "Test"));

            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isTrue();
        }

        @Test
        void deletesAllByWorkspace() {
            practiceRepository.save(createPractice("one", "One"));
            practiceRepository.save(createPractice("two", "Two"));

            Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("other-ws"));
            Practice otherPractice = createPractice("other", "Other");
            otherPractice.setWorkspace(otherWorkspace);
            practiceRepository.save(otherPractice);

            practiceRepository.deleteAllByWorkspaceId(workspace.getId());

            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isFalse();
            assertThat(practiceRepository.existsByWorkspaceId(otherWorkspace.getId())).isTrue();
        }
    }
}

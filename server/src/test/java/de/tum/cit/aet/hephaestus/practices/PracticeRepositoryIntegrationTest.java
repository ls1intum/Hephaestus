package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
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

        @Test
        void reviewTierFilteredQueryExcludesOnlyTheSilencedOnes() {
            Practice loud = createPractice("loud", "Loud");
            Practice measured = createPractice("measured", "Measured");
            measured.setReviewTier(PracticeReviewTier.MEASURE);
            Practice silent = createPractice("silent", "Silent");
            silent.setReviewTier(PracticeReviewTier.OFF);
            practiceRepository.saveAll(List.of(loud, measured, silent));

            List<Practice> result = practiceRepository.findByWorkspaceIdAndReviewTierNotAndArtifactKind(
                workspace.getId(),
                PracticeReviewTier.OFF,
                loud.getArtifactKind()
            );

            // MEASURE is included: it still runs a review, and the agent needs its criteria to run one.
            assertThat(result).extracting(Practice::getSlug).containsExactlyInAnyOrder("loud", "measured");
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

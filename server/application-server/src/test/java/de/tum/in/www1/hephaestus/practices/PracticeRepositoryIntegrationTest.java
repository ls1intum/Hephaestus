package de.tum.in.www1.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("PracticeRepository Integration")
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
        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("practice-test"));
    }

    private Practice createPractice(String slug, String name, String category) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCategory(category);
        practice.setDescription("Test description for " + slug);
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        return practice;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("saves and retrieves a practice with all fields")
        void savesAndRetrieves() {
            Practice practice = createPractice("test-slug", "Test Practice", "test-category");
            practice.setCriteria("Check for quality");
            practice.setActive(false);

            Practice saved = practiceRepository.save(practice);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();

            Practice found = practiceRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.getSlug()).isEqualTo("test-slug");
            assertThat(found.getName()).isEqualTo("Test Practice");
            assertThat(found.getCategory()).isEqualTo("test-category");
            assertThat(found.getDescription()).isEqualTo("Test description for test-slug");
            assertThat(found.getTriggerEvents().toString()).contains("PullRequestCreated");
            assertThat(found.getCriteria()).isEqualTo("Check for quality");
            assertThat(found.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Unique constraint")
    class UniqueConstraintTests {

        @Test
        @DisplayName("rejects duplicate slug within same workspace")
        void rejectsDuplicateSlugInWorkspace() {
            practiceRepository.save(createPractice("unique-slug", "First", "cat"));

            Practice duplicate = createPractice("unique-slug", "Second", "cat");
            assertThatThrownBy(() -> practiceRepository.saveAndFlush(duplicate)).isInstanceOf(
                DataIntegrityViolationException.class
            );
        }

        @Test
        @DisplayName("allows same slug in different workspaces")
        void allowsSameSlugInDifferentWorkspaces() {
            practiceRepository.save(createPractice("shared-slug", "First", "cat"));

            Workspace otherWorkspace = workspaceRepository.save(
                WorkspaceTestFactory.activeWorkspace("other-workspace")
            );
            Practice otherPractice = createPractice("shared-slug", "Second", "cat");
            otherPractice.setWorkspace(otherWorkspace);

            Practice saved = practiceRepository.saveAndFlush(otherPractice);
            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("findByWorkspaceIdAndActiveTrue returns only active practices")
        void findsActivePracticesOnly() {
            Practice active = createPractice("active", "Active", "cat");
            Practice inactive = createPractice("inactive", "Inactive", "cat");
            inactive.setActive(false);
            practiceRepository.save(active);
            practiceRepository.save(inactive);

            List<Practice> result = practiceRepository.findByWorkspaceIdAndActiveTrue(workspace.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSlug()).isEqualTo("active");
        }

        @Test
        @DisplayName("findByWorkspaceIdAndSlug returns matching practice")
        void findsBySlug() {
            practiceRepository.save(createPractice("target", "Target", "cat"));
            practiceRepository.save(createPractice("other", "Other", "cat"));

            var found = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), "target");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Target");
        }

        @Test
        @DisplayName("existsByWorkspaceId returns true when practices exist")
        void existsByWorkspace() {
            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isFalse();

            practiceRepository.save(createPractice("test", "Test", "cat"));

            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isTrue();
        }

        @Test
        @DisplayName("deleteAllByWorkspaceId removes all practices for workspace")
        void deletesAllByWorkspace() {
            practiceRepository.save(createPractice("one", "One", "cat"));
            practiceRepository.save(createPractice("two", "Two", "cat"));

            Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("other-ws"));
            Practice otherPractice = createPractice("other", "Other", "cat");
            otherPractice.setWorkspace(otherWorkspace);
            practiceRepository.save(otherPractice);

            practiceRepository.deleteAllByWorkspaceId(workspace.getId());

            assertThat(practiceRepository.existsByWorkspaceId(workspace.getId())).isFalse();
            assertThat(practiceRepository.existsByWorkspaceId(otherWorkspace.getId())).isTrue();
        }
    }
}

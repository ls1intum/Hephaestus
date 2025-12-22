package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubLabelMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Label Message Handler")
@Transactional
class GitHubLabelMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubLabelMessageHandler handler;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        Organization org = new Organization();
        org.setId(215361191L);
        org.setLogin("HephaestusTest");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setId(1000663383L);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner("HephaestusTest/TestRepository");
        testRepository.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin("HephaestusTest");
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("label");
    }

    @Test
    @DisplayName("Should process created label events")
    void shouldProcessCreatedLabelEvents() throws Exception {
        // Given
        GitHubLabelEventDTO event = loadPayload("label.created");

        // Verify label doesn't exist initially
        assertThat(labelRepository.findById(event.label().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(labelRepository.findById(event.label().id()))
            .isPresent()
            .get()
            .satisfies(label -> {
                assertThat(label.getId()).isEqualTo(event.label().id());
                assertThat(label.getName()).isEqualTo(event.label().name());
                assertThat(label.getColor()).isEqualTo(event.label().color());
                assertThat(label.getDescription()).isEqualTo(event.label().description());
            });
    }

    @Test
    @DisplayName("Should process edited label events")
    void shouldProcessEditedLabelEvents() throws Exception {
        // Given
        GitHubLabelEventDTO event = loadPayload("label.edited");

        // Create existing label with different data
        Label existingLabel = new Label();
        existingLabel.setId(event.label().id());
        existingLabel.setName("old-name");
        existingLabel.setColor("ffffff");
        existingLabel.setDescription("old description");
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        // When
        handler.handleEvent(event);

        // Then
        assertThat(labelRepository.findById(event.label().id()))
            .isPresent()
            .get()
            .satisfies(label -> {
                assertThat(label.getId()).isEqualTo(event.label().id());
                assertThat(label.getName()).isEqualTo(event.label().name());
                assertThat(label.getColor()).isEqualTo(event.label().color());
                assertThat(label.getDescription()).isEqualTo(event.label().description());
            });
    }

    @Test
    @DisplayName("Should process deleted label events")
    void shouldProcessDeletedLabelEvents() throws Exception {
        // Given
        GitHubLabelEventDTO event = loadPayload("label.deleted");

        // Create existing label
        Label existingLabel = new Label();
        existingLabel.setId(event.label().id());
        existingLabel.setName(event.label().name());
        existingLabel.setColor(event.label().color());
        existingLabel.setRepository(testRepository);
        labelRepository.save(existingLabel);

        // Verify it exists
        assertThat(labelRepository.findById(event.label().id())).isPresent();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(labelRepository.findById(event.label().id())).isEmpty();
    }

    private GitHubLabelEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubLabelEventDTO.class);
    }
}

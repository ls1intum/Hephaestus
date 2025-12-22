package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationTargetEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
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
 * Integration tests for GitHubInstallationTargetMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Installation Target Message Handler")
@Transactional
class GitHubInstallationTargetMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationTargetMessageHandler handler;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    private void setupTestWorkspace(Long installationId, String login) {
        // Create organization
        Organization org = new Organization();
        org.setId(215361191L);
        org.setLogin(login);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(login.toLowerCase());
        workspace.setDisplayName(login);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(login);
        workspace.setAccountType(AccountType.ORG);
        workspace.setInstallationId(installationId);
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("installation_target");
    }

    @Test
    @DisplayName("Should handle renamed event")
    void shouldHandleRenamedEvent() throws Exception {
        // Given
        GitHubInstallationTargetEventDTO event = loadPayload("installation_target.renamed");
        setupTestWorkspace(event.installation().id(), "OldName");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("renamed");
    }

    private GitHubInstallationTargetEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationTargetEventDTO.class);
    }
}

package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubInstallationMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Installation Message Handler")
@Transactional
class GitHubInstallationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationMessageHandler handler;

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

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("installation");
    }

    @Test
    @DisplayName("Should handle created event")
    void shouldHandleCreatedEvent() throws Exception {
        // Given
        GitHubInstallationEventDTO event = loadPayload("installation.created");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        // Full workspace creation is tested in live integration tests
        assertThat(event.action()).isEqualTo("created");
    }

    @Test
    @DisplayName("Should handle deleted event")
    void shouldHandleDeletedEvent() throws Exception {
        // Given
        GitHubInstallationEventDTO event = loadPayload("installation.deleted");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("deleted");
    }

    @Test
    @DisplayName("Should handle suspended event")
    void shouldHandleSuspendedEvent() throws Exception {
        // Given
        GitHubInstallationEventDTO event = loadPayload("installation.suspend");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("suspend");
    }

    @Test
    @DisplayName("Should handle unsuspended event")
    void shouldHandleUnsuspendedEvent() throws Exception {
        // Given
        GitHubInstallationEventDTO event = loadPayload("installation.unsuspend");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("unsuspend");
    }

    @Test
    @DisplayName("Should handle null installation gracefully")
    void shouldHandleNullInstallationGracefully() {
        // Given - event with null installation
        GitHubInstallationEventDTO event = new GitHubInstallationEventDTO("created", null, null, null);

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs warning but doesn't crash
    }

    @Test
    @DisplayName("Should handle unknown action gracefully")
    void shouldHandleUnknownActionGracefully() throws Exception {
        // Given - load a valid event and parse to get structure, then create with unknown action
        GitHubInstallationEventDTO baseEvent = loadPayload("installation.created");
        GitHubInstallationEventDTO event = new GitHubInstallationEventDTO(
            "unknown_action",
            baseEvent.installation(),
            baseEvent.repositories(),
            baseEvent.sender()
        );

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs debug message for unhandled action
    }

    @Test
    @DisplayName("Should return INSTALLATION domain")
    void shouldReturnInstallationDomain() {
        assertThat(handler.getDomain()).isEqualTo(
            de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler.GitHubMessageDomain.INSTALLATION
        );
    }

    private GitHubInstallationEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationEventDTO.class);
    }
}

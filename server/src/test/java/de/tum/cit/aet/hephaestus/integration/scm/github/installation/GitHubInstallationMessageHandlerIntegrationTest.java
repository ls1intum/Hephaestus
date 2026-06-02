package de.tum.cit.aet.hephaestus.integration.scm.github.installation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.github.installation.dto.GitHubInstallationEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for GitHubInstallationMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
class GitHubInstallationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationMessageHandler handler;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        // Ensure GitHub GitProvider exists - required by GithubLifecycleListener
        gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
    }

    @Test
    void shouldReturnCorrectEventKey() {
        assertThat(handler.key().eventType()).isEqualTo("installation.installation");
    }

    @Test
    void shouldHandleCreatedEvent() throws Exception {
        GitHubInstallationEventDTO event = loadPayload("installation.created");

        handler.handleEvent(event);

        // Then - handler processes without error
        // Full workspace creation is tested in live integration tests
        assertThat(event.action()).isEqualTo("created");
    }

    @Test
    void shouldHandleDeletedEvent() throws Exception {
        GitHubInstallationEventDTO event = loadPayload("installation.deleted");

        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("deleted");
    }

    @Test
    void shouldHandleSuspendedEvent() throws Exception {
        GitHubInstallationEventDTO event = loadPayload("installation.suspend");

        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("suspend");
    }

    @Test
    void shouldHandleUnsuspendedEvent() throws Exception {
        GitHubInstallationEventDTO event = loadPayload("installation.unsuspend");

        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("unsuspend");
    }

    @Test
    void shouldHandleNullInstallationGracefully() {
        // Given - event with null installation
        GitHubInstallationEventDTO event = new GitHubInstallationEventDTO("created", null, null, null);

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs warning but doesn't crash
    }

    @Test
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

    private GitHubInstallationEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationEventDTO.class);
    }
}

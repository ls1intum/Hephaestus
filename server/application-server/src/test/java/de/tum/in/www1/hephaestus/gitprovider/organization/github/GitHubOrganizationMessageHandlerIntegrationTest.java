package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadOrganization;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Organization Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubOrganizationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubOrganizationMessageHandler handler;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("organization.member_added is handled")
    void handlesMemberAdded(@GitHubPayload("organization.member_added") GHEventPayloadOrganization payload) {
        assertThat(payload.getAction()).isEqualTo("member_added");
        assertThat(payload.getMembership()).isNotNull();
        assertThat(payload.getMembership().getUser().getLogin()).isEqualTo("hephaestususer");

        // When
        handler.handleEvent(payload);
        // Then: no exception and core fields present (behavior currently logs only)
        assertThat(payload.getOrganization()).isNotNull();
        assertThat(payload.getSender()).isNotNull();
    }

    @Test
    @DisplayName("organization.member_removed is handled")
    void handlesMemberRemoved(@GitHubPayload("organization.member_removed") GHEventPayloadOrganization payload) {
        assertThat(payload.getAction()).isEqualTo("member_removed");
        assertThat(payload.getMembership()).isNotNull();
        assertThat(payload.getMembership().getUser().getLogin()).isEqualTo("hephaestususer");

        handler.handleEvent(payload);
        assertThat(payload.getOrganization()).isNotNull();
        assertThat(payload.getSender()).isNotNull();
    }

    @Test
    @DisplayName("organization.member_invited is handled")
    void handlesMemberInvited(@GitHubPayload("organization.member_invited") GHEventPayloadOrganization payload) {
        assertThat(payload.getAction()).isEqualTo("member_invited");
        assertThat(payload.getInvitation()).isNotNull();
        assertThat(payload.getInvitation().getLogin()).isEqualTo("hephaestususer");
        assertThat(payload.getUser()).isNotNull();

        handler.handleEvent(payload);
        assertThat(payload.getOrganization()).isNotNull();
        assertThat(payload.getSender()).isNotNull();
    }

    @Test
    @DisplayName("organization.renamed is handled")
    void handlesRenamed(@GitHubPayload("organization.renamed") GHEventPayloadOrganization payload) {
        assertThat(payload.getAction()).isEqualTo("renamed");
        assertThat(payload.getChanges()).isNotNull();
        assertThat(payload.getChanges().getLogin()).isNotNull();
        assertThat(payload.getChanges().getLogin().getFrom()).isEqualTo("HephaestusTest2");

        handler.handleEvent(payload);
        assertThat(payload.getOrganization()).isNotNull();
    }
}

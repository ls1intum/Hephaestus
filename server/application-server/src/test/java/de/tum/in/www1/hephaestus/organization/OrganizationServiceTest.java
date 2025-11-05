package de.tum.in.www1.hephaestus.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Organization Service")
class OrganizationServiceTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("upsertIdentity creates new organization when none exists")
    void createsNewOrganization() {
        // When
        Organization org = organizationService.upsertIdentity(12345L, "test-org");

        // Then
        assertThat(org).isNotNull();
        assertThat(org.getId()).isEqualTo(12345L);
        assertThat(org.getGithubId()).isEqualTo(12345L);
        assertThat(org.getLogin()).isEqualTo("test-org");
    }

    @Test
    @DisplayName("upsertIdentity updates existing organization found by githubId")
    void updatesExistingOrganizationByGithubId() {
        // Given
        Organization existing = organizationService.upsertIdentity(12345L, "old-name");

        // When
        Organization updated = organizationService.upsertIdentity(12345L, "new-name");

        // Then
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getGithubId()).isEqualTo(12345L);
        assertThat(updated.getLogin()).isEqualTo("new-name");
        assertThat(organizationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("upsertIdentity handles concurrent calls gracefully")
    void handlesConcurrentCallsGracefully() {
        // Given - Create an organization
        Organization first = organizationService.upsertIdentity(12345L, "test-org");

        // When - Call upsert again with the same id (simulating concurrent access)
        Organization second = organizationService.upsertIdentity(12345L, "test-org-renamed");

        // Then - Should update the existing organization, not create a new one
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getGithubId()).isEqualTo(12345L);
        assertThat(second.getLogin()).isEqualTo("test-org-renamed");
        assertThat(organizationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("upsertIdentity throws exception when login is null")
    void throwsExceptionWhenLoginIsNull() {
        assertThatThrownBy(() -> organizationService.upsertIdentity(12345L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("login required");
    }

    @Test
    @DisplayName("upsertIdentity throws exception when login is blank")
    void throwsExceptionWhenLoginIsBlank() {
        assertThatThrownBy(() -> organizationService.upsertIdentity(12345L, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("login required");
    }

    @Test
    @DisplayName("upsertIdentityAndAttachInstallation creates organization with installation")
    void createsOrganizationWithInstallation() {
        // When
        Organization org = organizationService.upsertIdentityAndAttachInstallation(12345L, "test-org", 99999L);

        // Then
        assertThat(org).isNotNull();
        assertThat(org.getId()).isEqualTo(12345L);
        assertThat(org.getGithubId()).isEqualTo(12345L);
        assertThat(org.getLogin()).isEqualTo("test-org");
        assertThat(org.getInstallationId()).isEqualTo(99999L);
    }

    @Test
    @DisplayName("upsertIdentityAndAttachInstallation updates installation on existing organization")
    void updatesInstallationOnExistingOrganization() {
        // Given
        Organization existing = organizationService.upsertIdentity(12345L, "test-org");

        // When
        Organization updated = organizationService.upsertIdentityAndAttachInstallation(
            12345L,
            "test-org",
            99999L
        );

        // Then
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getInstallationId()).isEqualTo(99999L);
        assertThat(organizationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getByInstallationId returns organization when it exists")
    void returnsOrganizationByInstallationId() {
        // Given
        organizationService.upsertIdentityAndAttachInstallation(12345L, "test-org", 99999L);

        // When
        var result = organizationService.getByInstallationId(99999L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getLogin()).isEqualTo("test-org");
    }

    @Test
    @DisplayName("getByInstallationId returns empty when organization does not exist")
    void returnsEmptyWhenOrganizationNotFoundByInstallationId() {
        // When
        var result = organizationService.getByInstallationId(99999L);

        // Then
        assertThat(result).isEmpty();
    }
}

package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.util.Optional;
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

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("organization.member_added persists membership and user")
    void memberAddedCreatesMembership(@GitHubPayload("organization.member_added") GHEventPayloadOrganization payload) {
        handler.handleEvent(payload);

        var organization = organizationRepository.findByGithubId(payload.getOrganization().getId()).orElseThrow();
        assertThat(organization.getLogin()).isEqualTo(payload.getOrganization().getLogin());
        assertThat(organization.getAvatarUrl()).isNotBlank();

        var memberships = membershipRepository.findAll();
        assertThat(memberships)
            .singleElement()
            .satisfies(m -> {
                assertThat(m.getOrganizationId()).isEqualTo(payload.getOrganization().getId());
                assertThat(m.getUserId()).isEqualTo(payload.getMembership().getUser().getId());
                assertThat(m.getRole()).isEqualTo("MEMBER");
            });
    }

    @Test
    @DisplayName("organization.member_removed deletes membership")
    void memberRemovedDeletesMembership(
        @GitHubPayload("organization.member_added") GHEventPayloadOrganization added,
        @GitHubPayload("organization.member_removed") GHEventPayloadOrganization removed
    ) {
        handler.handleEvent(added);
        assertThat(membershipRepository.findAll()).isNotEmpty();

        handler.handleEvent(removed);

        assertThat(membershipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("organization.member_invited is ignored for ETL")
    void memberInvitedIsIgnored(@GitHubPayload("organization.member_invited") GHEventPayloadOrganization payload) {
        handler.handleEvent(payload);

        Optional<Organization> organization = organizationRepository.findByGithubId(payload.getOrganization().getId());
        assertThat(organization).isPresent();
        assertThat(membershipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("organization.renamed updates stored login")
    void renamedUpdatesLogin(@GitHubPayload("organization.renamed") GHEventPayloadOrganization payload) {
        organizationService.upsertIdentity(
            payload.getOrganization().getId(),
            payload.getChanges().getLogin().getFrom()
        );

        handler.handleEvent(payload);

        var organization = organizationRepository.findByGithubId(payload.getOrganization().getId()).orElseThrow();
        assertThat(organization.getLogin()).isEqualTo(payload.getOrganization().getLogin());
    }
}

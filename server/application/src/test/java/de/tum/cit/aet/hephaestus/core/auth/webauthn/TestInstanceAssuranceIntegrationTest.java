package de.tum.cit.aet.hephaestus.core.auth.webauthn;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.TokenConstraints;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspacePasskeyPolicyController;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Signed, old browser sessions retain ordinary authorization while added assurance is suspended. */
@TestPropertySource(
        properties = {
            "hephaestus.auth.assurance-enforced=false",
            "hephaestus.auth.passkeys.instance-admin-required=true",
            "hephaestus.auth.passkeys.workspace-admin-required=true"
        })
class TestInstanceAssuranceIntegrationTest extends RealAuthIntegrationTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private HephaestusJwtIssuer issuer;

    @Value("${hephaestus.auth.cookie-name:__Host-HEPHAESTUS_AT}")
    private String cookie;

    private Account account(boolean admin) {
        var account = new Account("Test-instance administrator");
        if (admin) {
            account.setAppRole(Account.AppRole.APP_ADMIN);
        }
        account.setPasskeyProtectionEnabled(true);
        account.setPasskeyRecoveryRequired(true);
        return accounts.saveAndFlush(account);
    }

    private String token(Account account) {
        return issuer.issue(
                        Objects.requireNonNull(account.getId()),
                        new TokenConstraints(
                                Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().minus(Duration.ofHours(2)),
                                null,
                                null),
                        null)
                .value();
    }

    private Workspace workspace(Account account, Membership.WorkspaceRole role) {
        var workspace = WorkspaceTestFixtures.activeWorkspace("assurance-" + UUID.randomUUID());
        workspace.setAdminPasskeyRequired(true);
        workspace = workspaces.saveAndFlush(workspace);
        var membership = new Membership();
        membership.setId(new Membership.Id(workspace.getId(), account.getId()));
        membership.setWorkspace(workspace);
        membership.setRole(role);
        memberships.saveAndFlush(membership);
        return workspace;
    }

    private String csrf() {
        var response = client.get()
                .uri("/identity-providers")
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(Void.class);
        return Objects.requireNonNull(response.getResponseCookies().getFirst("__Host-XSRF-TOKEN"))
                .getValue();
    }

    private WebTestClient.RequestBodySpec patchPolicy(Workspace workspace, String token, String csrf) {
        return client.patch()
                .uri("/workspaces/" + workspace.getWorkspaceSlug() + "/passkey-policy")
                .header(HttpHeaders.COOKIE, cookie + "=" + token + "; __Host-XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf);
    }

    @Test
    void shouldAllowAdminWithOldOAuthSessionWithoutInventingPasskeyProof() {
        var account = account(true);
        String oauth = token(account);
        client.get()
                .uri("/admin/users")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Void.class);
        client.get()
                .uri("/user/passkeys")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.assuranceEnforced")
                .isEqualTo(false)
                .jsonPath("$.verified")
                .isEqualTo(false)
                .jsonPath("$.protectionEnabled")
                .isEqualTo(true)
                .jsonPath("$.recoveryRequired")
                .isEqualTo(true);
        assertThat(accounts.findById(Objects.requireNonNull(account.getId()))
                        .orElseThrow()
                        .isPasskeyProtectionEnabled())
                .isTrue();
        String csrf = csrf();
        client.post()
                .uri("/admin/research-disclosures/removal-checks")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth + "; __Host-XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Void.class);
    }

    @Test
    void shouldKeepOwnerRoleAndCsrfWhileChangingBothPolicyDirections() {
        var owner = account(false);
        var workspace = workspace(owner, Membership.WorkspaceRole.OWNER);
        String oauth = token(owner);
        String csrf = csrf();
        patchPolicy(workspace, oauth, csrf)
                .bodyValue(new WorkspacePasskeyPolicyController.UpdateWorkspacePasskeyPolicyDTO(false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.assuranceEnforced")
                .isEqualTo(false)
                .jsonPath("$.required")
                .isEqualTo(false);
        patchPolicy(workspace, oauth, csrf)
                .bodyValue(new WorkspacePasskeyPolicyController.UpdateWorkspacePasskeyPolicyDTO(true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.required")
                .isEqualTo(true);
        client.patch()
                .uri("/workspaces/" + workspace.getWorkspaceSlug() + "/passkey-policy")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth)
                .bodyValue(new WorkspacePasskeyPolicyController.UpdateWorkspacePasskeyPolicyDTO(false))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        assertThat(workspaces.findById(workspace.getId()).orElseThrow().isAdminPasskeyRequired())
                .isTrue();
        var member = account(false);
        var memberWorkspace = workspace(member, Membership.WorkspaceRole.MEMBER);
        patchPolicy(memberWorkspace, token(member), csrf)
                .bodyValue(new WorkspacePasskeyPolicyController.UpdateWorkspacePasskeyPolicyDTO(false))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        assertThat(workspaces.findById(memberWorkspace.getId()).orElseThrow().isAdminPasskeyRequired())
                .isTrue();
    }

    @Test
    void shouldKeepInstanceAdministrationAndResearchRestrictedToAdministrators() {
        String oauth = token(account(false));
        client.get()
                .uri("/admin/users")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        String csrf = csrf();
        client.post()
                .uri("/admin/research-disclosures/removal-checks")
                .header(HttpHeaders.COOKIE, cookie + "=" + oauth + "; __Host-XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        client.get()
                .uri("/admin/users")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(Void.class);
    }
}

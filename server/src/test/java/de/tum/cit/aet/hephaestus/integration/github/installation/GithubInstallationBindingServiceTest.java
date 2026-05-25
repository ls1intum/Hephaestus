package de.tum.cit.aet.hephaestus.integration.github.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallationExpiredException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallerIdentityMismatchException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallerIdentityNotLinkedException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.LegacyUnboundRowException;
import de.tum.cit.aet.hephaestus.integration.identity.HephaestusUser;
import de.tum.cit.aet.hephaestus.integration.identity.IntegrationIdentity;
import de.tum.cit.aet.hephaestus.integration.identity.IntegrationIdentityRepository;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-level guards on the bind path:
 * <ul>
 *   <li>happy path (identity match) creates Connection, transitions, deletes unbound;</li>
 *   <li>identity mismatch (confused-deputy attack) is rejected with an opaque message;</li>
 *   <li>unlinked actor returns 412-equivalent so the controller can show a link CTA;</li>
 *   <li>expired / legacy / cross-workspace / missing-row / missing-workspace stay rejected.</li>
 * </ul>
 * No Spring context — pure Mockito so the suite stays fast.
 */
@DisplayName("GithubInstallationBindingService — unit")
class GithubInstallationBindingServiceTest extends BaseUnitTest {

    private static final long INSTALLER_GH_ID = 555_111L;

    @Mock private GithubInstallationUnboundRepository unboundRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private ConnectionService connectionService;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private IntegrationIdentityRepository identityRepository;
    @Mock private Workspace workspace;
    @Mock private HephaestusUser authenticatedUser;
    @Mock private HephaestusUser otherUser;

    private GithubInstallationBindingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GithubInstallationBindingService(
            unboundRepository, connectionRepository, connectionService,
            workspaceRepository, identityRepository
        );
        org.mockito.Mockito.lenient().when(authenticatedUser.getId()).thenReturn(7L);
        org.mockito.Mockito.lenient().when(authenticatedUser.getKeycloakSubject()).thenReturn("alice-keycloak-sub");
        org.mockito.Mockito.lenient().when(otherUser.getId()).thenReturn(8L);
    }

    /** Convenience: stub identity repo to claim the installer maps to a given Hephaestus user. */
    private void stubIdentityLink(HephaestusUser linkedUser) {
        IntegrationIdentity identity = org.mockito.Mockito.mock(IntegrationIdentity.class);
        when(identity.getHephaestusUser()).thenReturn(linkedUser);
        when(identityRepository.findByKindAndExternalId(
            IntegrationKind.GITHUB, Long.toString(INSTALLER_GH_ID)))
            .thenReturn(List.of(identity));
    }

    /** Convenience: build an unbound row with the installer column populated. */
    private GithubInstallationUnbound unboundWithInstaller(long installationId) {
        GithubInstallationUnbound u = new GithubInstallationUnbound(installationId);
        u.setAccountLogin("acme-corp");
        u.setInstallerGithubUserId(INSTALLER_GH_ID);
        u.setInstallerLogin("alice-on-github");
        return u;
    }

    @Test
    @DisplayName("happy path: installer identity matches authenticated user → bind succeeds")
    void happyPath_identityMatches() {
        long installationId = 100_001L;
        long workspaceId = 42L;
        GithubInstallationUnbound unbound = unboundWithInstaller(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        stubIdentityLink(authenticatedUser);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId))).thenReturn(List.of());
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId, IntegrationKind.GITHUB, Long.toString(installationId))).thenReturn(Optional.empty());
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> {
                Connection c = inv.getArgument(0);
                c.setState(IntegrationState.ACTIVE);
                return c;
            });

        Connection bound = service.bind(installationId, workspaceId, authenticatedUser);

        assertThat(bound.getState()).isEqualTo(IntegrationState.ACTIVE);
        verify(unboundRepository).delete(unbound);

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().actorRef()).isEqualTo("alice-keycloak-sub");
    }

    @Test
    @DisplayName("CVE: identity mismatch (attacker submits victim's installation_id) → 403 with opaque message")
    void identityMismatch_attackerClaimsVictimInstallation_rejected() {
        long installationId = 666_666L;
        long workspaceId = 42L;
        GithubInstallationUnbound unbound = unboundWithInstaller(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        // Installer's GitHub identity is linked to a DIFFERENT Hephaestus user.
        stubIdentityLink(otherUser);

        assertThatThrownBy(() -> service.bind(installationId, workspaceId, authenticatedUser))
            .isInstanceOf(InstallerIdentityMismatchException.class)
            .hasMessage("installer_identity_mismatch"); // opaque — no oracle

        verify(workspaceRepository, never()).findById(anyLong());
        verify(connectionRepository, never()).save(any(Connection.class));
        verify(unboundRepository, never()).delete(any(GithubInstallationUnbound.class));
    }

    @Test
    @DisplayName("CVE: unlinked installer (no Hephaestus identity at all) → 412 link-first")
    void identityNotLinked_returns412() {
        long installationId = 100_002L;
        GithubInstallationUnbound unbound = unboundWithInstaller(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        // Identity row exists but no Hephaestus user is linked.
        IntegrationIdentity orphan = org.mockito.Mockito.mock(IntegrationIdentity.class);
        when(orphan.getHephaestusUser()).thenReturn(null);
        when(identityRepository.findByKindAndExternalId(
            IntegrationKind.GITHUB, Long.toString(INSTALLER_GH_ID))).thenReturn(List.of(orphan));

        assertThatThrownBy(() -> service.bind(installationId, 42L, authenticatedUser))
            .isInstanceOf(InstallerIdentityNotLinkedException.class);

        verify(connectionRepository, never()).save(any(Connection.class));
    }

    @Test
    @DisplayName("CVE: zero identity rows for the installer → 412 link-first")
    void noIdentityRow_returns412() {
        long installationId = 100_003L;
        when(unboundRepository.findById(installationId))
            .thenReturn(Optional.of(unboundWithInstaller(installationId)));
        when(identityRepository.findByKindAndExternalId(
            IntegrationKind.GITHUB, Long.toString(INSTALLER_GH_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service.bind(installationId, 42L, authenticatedUser))
            .isInstanceOf(InstallerIdentityNotLinkedException.class);
    }

    @Test
    @DisplayName("Legacy unbound row (no installer column) → 409 with uninstall+reinstall message")
    void legacyUnboundRow_returns409() {
        long installationId = 100_004L;
        GithubInstallationUnbound legacy = new GithubInstallationUnbound(installationId);
        legacy.setAccountLogin("legacy-team");
        // installer_github_user_id is null
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.bind(installationId, 42L, authenticatedUser))
            .isInstanceOf(LegacyUnboundRowException.class)
            .hasMessageContaining("Legacy");

        verify(identityRepository, never()).findByKindAndExternalId(any(), any());
    }

    @Test
    @DisplayName("Expired unbound row → 410 GONE")
    void expiredUnboundRow_returns410() throws Exception {
        long installationId = 100_005L;
        GithubInstallationUnbound expired = unboundWithInstaller(installationId);
        // Reflectively set expiresAt to the past (field is DB-default-driven, no setter).
        var field = GithubInstallationUnbound.class.getDeclaredField("expiresAt");
        field.setAccessible(true);
        field.set(expired, Instant.now().minusSeconds(86_400));
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.bind(installationId, 42L, authenticatedUser))
            .isInstanceOf(InstallationExpiredException.class);

        verify(identityRepository, never()).findByKindAndExternalId(any(), any());
    }

    @Test
    @DisplayName("cross-workspace collision still rejected (post-identity-check)")
    void crossWorkspaceCollision() {
        long installationId = 100_006L;
        long workspaceA = 7L;
        long workspaceB = 99L;

        Workspace ownerWorkspace = org.mockito.Mockito.mock(Workspace.class);
        when(ownerWorkspace.getId()).thenReturn(workspaceA);

        GithubInstallationUnbound unbound = unboundWithInstaller(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        stubIdentityLink(authenticatedUser);

        Workspace targetWorkspace = org.mockito.Mockito.mock(Workspace.class);
        org.mockito.Mockito.lenient().when(targetWorkspace.getId()).thenReturn(workspaceB);
        when(workspaceRepository.findById(workspaceB)).thenReturn(Optional.of(targetWorkspace));

        Connection preExisting = new Connection(
            ownerWorkspace,
            IntegrationKind.GITHUB,
            Long.toString(installationId),
            new ConnectionConfig.GitHubAppConfig(installationId, "team-a", null, java.util.Set.of())
        );
        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId))).thenReturn(List.of(preExisting));

        assertThatThrownBy(() -> service.bind(installationId, workspaceB, authenticatedUser))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already bound to workspace=" + workspaceA);

        verify(connectionRepository, never()).save(any(Connection.class));
        verify(unboundRepository, never()).delete(any(GithubInstallationUnbound.class));
    }

    @Test
    @DisplayName("idempotent re-bind: same workspace, same user → transitions without re-creating")
    void idempotentReBind() {
        long installationId = 100_007L;
        long workspaceId = 17L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);

        GithubInstallationUnbound unbound = unboundWithInstaller(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        stubIdentityLink(authenticatedUser);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        Connection existing = new Connection(
            workspace, IntegrationKind.GITHUB, Long.toString(installationId),
            new ConnectionConfig.GitHubAppConfig(installationId, null, null, java.util.Set.of())
        );
        existing.setState(IntegrationState.PENDING);

        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId))).thenReturn(List.of(existing));
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId, IntegrationKind.GITHUB, Long.toString(installationId))).thenReturn(Optional.of(existing));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> { Connection c = inv.getArgument(0); c.setState(IntegrationState.ACTIVE); return c; });

        Connection result = service.bind(installationId, workspaceId, authenticatedUser);

        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        verify(connectionRepository, never()).save(any(Connection.class));
        verify(connectionService, times(1)).transition(any(Connection.class), any(TransitionRequest.class));
        verify(unboundRepository).delete(unbound);
    }

    @Test
    @DisplayName("404 path: missing unbound row → NoSuchElementException (BEFORE identity check)")
    void missingUnboundRow() {
        long missing = 999_999L;
        when(unboundRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(missing, 1L, authenticatedUser))
            .isInstanceOf(NoSuchElementException.class);

        verify(identityRepository, never()).findByKindAndExternalId(any(), any());
        verify(workspaceRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("404 path: missing workspace → EntityNotFoundException (AFTER identity check passes)")
    void missingWorkspace() {
        long installationId = 100_008L;
        long workspaceId = 12345L;
        when(unboundRepository.findById(installationId))
            .thenReturn(Optional.of(unboundWithInstaller(installationId)));
        stubIdentityLink(authenticatedUser);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(installationId, workspaceId, authenticatedUser))
            .isInstanceOf(EntityNotFoundException.class);

        verify(connectionRepository, never()).save(any(Connection.class));
        verify(unboundRepository, never()).delete(any(GithubInstallationUnbound.class));
    }

    @Test
    @DisplayName("authenticatedUser must be non-null")
    void nullAuthenticatedUser_rejected() {
        assertThatThrownBy(() -> service.bind(1L, 1L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authenticatedUser");
        verify(unboundRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("actorRef on the transition request is the authenticated user's Keycloak subject")
    void actorRefThreadedThrough_isKeycloakSubject() {
        long installationId = 100_009L;
        long workspaceId = 8L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);
        when(unboundRepository.findById(installationId))
            .thenReturn(Optional.of(unboundWithInstaller(installationId)));
        stubIdentityLink(authenticatedUser);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(connectionRepository.findByKindAndInstanceKey(eq(IntegrationKind.GITHUB), any(String.class)))
            .thenReturn(List.of());
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            anyLong(), eq(IntegrationKind.GITHUB), any(String.class))).thenReturn(Optional.empty());
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> { Connection c = inv.getArgument(0); c.setState(IntegrationState.ACTIVE); return c; });

        service.bind(installationId, workspaceId, authenticatedUser);

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().actorRef()).isEqualTo("alice-keycloak-sub");
        assertThat(req.getValue().actorKind()).isEqualTo("ADMIN");
    }
}

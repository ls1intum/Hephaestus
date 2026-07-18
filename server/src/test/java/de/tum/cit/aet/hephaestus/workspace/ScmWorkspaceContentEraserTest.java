package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-level contract of the SCM erase choke point: it must delegate the cascade to
 * {@link WorkspaceRepositoryMonitorService#deleteRepositoryIfOrphaned} (never hand-roll a delete),
 * emit the derived-row event BEFORE dropping monitors, refresh the consumer once, and be a
 * no-exception no-op on an already-erased workspace. Cross-tenant survival itself is proven against
 * real Postgres in {@code ScmWorkspaceErasureIntegrationTest} — a mock cannot enforce the orphan guard.
 */
class ScmWorkspaceContentEraserTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceRepositoryMonitorService repositoryMonitorService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Mock
    private ObjectProvider<IntegrationNatsConsumer> natsConsumerService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ScmWorkspaceContentEraser eraser;

    @BeforeEach
    void setUp() {
        eraser = new ScmWorkspaceContentEraser(
            workspaceRepository,
            repositoryToMonitorRepository,
            repositoryMonitorService,
            teamRepository,
            organizationMembershipRepository,
            new NatsConnectionProperties(false, null, null, 7, null),
            natsConsumerService,
            eventPublisher
        );
    }

    private Workspace workspaceWithMonitors(String... namesWithOwner) {
        Workspace workspace = newWorkspace();
        List<RepositoryToMonitor> monitors = List.of(namesWithOwner)
            .stream()
            .map(name -> {
                RepositoryToMonitor monitor = new RepositoryToMonitor();
                monitor.setNameWithOwner(name);
                monitor.setWorkspace(workspace);
                return monitor;
            })
            .toList();
        workspace.getRepositoriesToMonitor().addAll(monitors);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(monitors);
        return workspace;
    }

    @Test
    void erase_delegatesTheCascadeToTheOrphanGuardedDeleteForEveryMonitoredRepository() {
        workspaceWithMonitors("acme/alpha", "acme/beta");

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        verify(repositoryMonitorService).deleteRepositoryIfOrphaned("acme/alpha");
        verify(repositoryMonitorService).deleteRepositoryIfOrphaned("acme/beta");
    }

    @Test
    void erase_clearsThisWorkspacesMonitorsAndFlushesBeforeTheOrphanCheckReadsThem() {
        Workspace workspace = workspaceWithMonitors("acme/alpha");

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        assertThat(workspace.getRepositoriesToMonitor()).isEmpty();
        // saveAndFlush, not save: a pending orphanRemoval DELETE is invisible to countByNameWithOwner.
        InOrder order = inOrder(workspaceRepository, repositoryMonitorService);
        order.verify(workspaceRepository).saveAndFlush(workspace);
        order.verify(repositoryMonitorService).deleteRepositoryIfOrphaned("acme/alpha");
    }

    @Test
    void erase_publishesTheDerivedRowEventBeforeDroppingTheArtifactsItPointsAt() {
        workspaceWithMonitors("acme/alpha");

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        InOrder order = inOrder(eventPublisher, repositoryMonitorService);
        order.verify(eventPublisher).publishEvent(captor.capture());
        order.verify(repositoryMonitorService).deleteRepositoryIfOrphaned("acme/alpha");
        assertThat(captor.getValue()).isEqualTo(new ScmMirrorErasedEvent(WORKSPACE_ID));
    }

    @Test
    void erase_isIdempotent_secondRunFindsNoMonitorsAndDeletesNothing() {
        Workspace workspace = newWorkspace();
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);
        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        verify(repositoryMonitorService, never()).deleteRepositoryIfOrphaned(any());
        verifyNoInteractions(teamRepository, organizationMembershipRepository);
    }

    @Test
    void erase_onMissingWorkspace_isASilentNoOp() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        verifyNoInteractions(eventPublisher, repositoryMonitorService, teamRepository);
    }

    /**
     * The guard is defensive only: the {@code unique} {@code organization_id} column means no second
     * workspace can be bound to the org today, so this count is always 0 in production. Pinned with a
     * stub so the guard keeps working if that 1:1 mapping is ever relaxed.
     */
    @Test
    void erase_keepsOrgTierMirrorWhenAnotherNonPurgedWorkspaceIsStillBoundToTheOrganization() {
        Workspace workspace = workspaceWithMonitors("acme/alpha");
        workspace.setOrganization(organization(7L, "acme"));
        when(workspaceRepository.countOtherActiveWorkspacesForOrganization(7L, WORKSPACE_ID)).thenReturn(1L);

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        verifyNoInteractions(teamRepository, organizationMembershipRepository);
    }

    @Test
    void erase_dropsOrgTierMirrorOnlyWhenThisIsTheLastWorkspaceForTheOrganization() {
        Workspace workspace = workspaceWithMonitors("acme/alpha");
        workspace.setOrganization(organization(7L, "acme"));
        when(workspaceRepository.countOtherActiveWorkspacesForOrganization(7L, WORKSPACE_ID)).thenReturn(0L);
        Team team = new Team();
        when(teamRepository.findByOrganizationIgnoreCaseAndProviderId("acme", 3L)).thenReturn(List.of(team));

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        verify(teamRepository).deleteAll(List.of(team));
        verify(organizationMembershipRepository).deleteByOrganizationId(7L);
    }

    @Test
    void erase_releasesTheOrganizationLinkAndFlushesItBeforeTheLastTenantGuardCounts() {
        Workspace workspace = workspaceWithMonitors("acme/alpha");
        workspace.setOrganization(organization(7L, "acme"));
        when(workspaceRepository.countOtherActiveWorkspacesForOrganization(7L, WORKSPACE_ID)).thenReturn(0L);

        eraser.eraseWorkspaceScmMirror(WORKSPACE_ID);

        // The organization binding is exclusive (unique organization_id), so a retained link would
        // squat that organization's single binding slot forever — a disconnect does not purge, so
        // no other workspace could ever install the same organization.
        assertThat(workspace.getOrganization()).isNull();
        // Flushed first, or the guard's COUNT still reads the stale link.
        InOrder order = inOrder(workspaceRepository);
        order.verify(workspaceRepository, times(2)).saveAndFlush(workspace);
        order.verify(workspaceRepository).countOtherActiveWorkspacesForOrganization(7L, WORKSPACE_ID);
    }

    private static Workspace newWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        return workspace;
    }

    private static Organization organization(long id, String login) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setLogin(login);
        IdentityProvider provider = new IdentityProvider();
        provider.setId(3L);
        organization.setProvider(provider);
        return organization;
    }
}

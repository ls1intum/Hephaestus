package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import io.micrometer.common.util.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single workspace-scoped choke point that erases every SCM-mirrored row a workspace is the last
 * tenant to hold — the GitHub/GitLab counterpart of {@code SlackWorkspaceContentEraser} and the
 * Outline disconnect-erase, so all four integrations now answer the same two erase triggers with
 * one shared implementation each.
 *
 * <p><b>Two triggers, one code path.</b> Driven by (1) the admin disconnect
 * ({@code GithubConnectionStrategy#revoke} / {@code GitlabConnectionStrategy#revoke}, invoked from
 * inside the fenced {@code ConnectionService#disconnect} transaction) and (2) the workspace purge
 * ({@code workspace.adapter.ScmWorkspacePurgeAdapter}, order -200). Both erase the identical row
 * set by construction, which is the invariant Slack/Outline already document and this class now
 * extends to SCM.
 *
 * <p><b>Hard-delete, NOT a tombstone — a different operation from the drift sweeps.</b> The SCM
 * deletion sweeps mark upstream-vanished rows with a recoverable {@code deletedAt} tombstone
 * because the source may merely have hidden them; that is a sync-fidelity feature and is
 * reversible. This class is the opposite: an irreversible {@code DELETE} run when the lawful basis
 * for holding the mirror is severed. The two paths deliberately share no code, and neither may be
 * implemented in terms of the other — a tombstoned row is still queryable retained personal data,
 * so it would not satisfy the disconnect/purge trigger, and a hard delete would destroy data the
 * drift sweep expects to be able to resurrect.
 *
 * <p><b>Cross-tenant safety is the whole design.</b> SCM tables carry no {@code workspace_id};
 * {@code repository} and its cascade are SHARED across every workspace that monitors the same
 * source repository. Erasure is therefore workspace-scoped by construction: this class removes
 * <em>this</em> workspace's {@code repository_to_monitor} rows, flushes, and then delegates the
 * actual cascade to {@link WorkspaceRepositoryMonitorService#deleteRepositoryIfOrphaned(String)},
 * which drops the shared row only when no monitor anywhere still points at it. A repository that
 * another tenant still monitors survives — correctly: the surviving tenant's basis for holding it
 * persists, while the erased tenant's access path (monitor row, NATS consumer filter, and every
 * workspace-scoped query) is gone. There is no global delete anywhere in this class.
 *
 * <p><b>Derived rows.</b> {@code practices} observations/feedback over {@code PULL_REQUEST}/
 * {@code ISSUE} artifacts and {@code activity_event} rows mirror SCM content (soft artifact refs
 * plus quoted content in the {@code evidence} jsonb) but live in modules that already depend on
 * {@code workspace}. They are erased through the synchronous, in-transaction
 * {@link ScmMirrorErasedEvent} rather than a direct port call, which would close a Spring Modulith
 * cycle. The event is published FIRST, so derived rows go before the artifacts they reference.
 *
 * <p><b>Retained on purpose.</b> {@code sync_job}, {@code connection_activity} and
 * {@code connection_audit} survive both triggers for all four integration kinds — they are
 * operational audit (kind/type/status/timestamps only, no mirrored third-party content, capped per
 * connection by the sync-job pruner) and retaining them uniformly with Slack/Outline is what makes
 * a disconnect auditable after the fact. Global identity rows ({@code user}, {@code organization},
 * {@code identity_provider}) are likewise never touched: they are instance-global identity rows, and
 * person-level erasure is a separate operator process.
 *
 * <p><b>Ordering and idempotency.</b> Sync and consumers are already stopped before this runs —
 * {@code ConnectionService#disconnect} reaps stale leases and refuses with a retryable 409 while a
 * sync job is in flight, and {@code WorkspaceLifecycleService#purgeWorkspace} stops NATS consumers
 * in step 1 — so this never races an in-flight writer and cannot deadlock against one. {@code
 * @Transactional} with default {@code REQUIRED} propagation joins the caller's transaction rather
 * than opening its own. Every step is a delete-if-present, so a second run erases 0 rows and
 * throws nothing.
 */
@Component
@WorkspaceAgnostic("Erases instance-global SCM rows the workspace is the last tenant to monitor")
public class ScmWorkspaceContentEraser {

    private static final Logger log = LoggerFactory.getLogger(ScmWorkspaceContentEraser.class);

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepositoryMonitorService repositoryMonitorService;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final NatsConnectionProperties natsProperties;

    /** Absent under the webhook runtime role. */
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumerService;

    private final ApplicationEventPublisher eventPublisher;

    public ScmWorkspaceContentEraser(
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepositoryMonitorService repositoryMonitorService,
        TeamRepository teamRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        NatsConnectionProperties natsProperties,
        ObjectProvider<IntegrationNatsConsumer> natsConsumerService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryMonitorService = repositoryMonitorService;
        this.teamRepository = teamRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.natsProperties = natsProperties;
        this.natsConsumerService = natsConsumerService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Irreversibly erases the workspace's SCM mirror. Idempotent: an already-erased (or
     * never-populated) workspace deletes 0 rows and completes normally.
     *
     * @param workspaceId the tenant whose SCM mirror is erased
     */
    @Transactional
    public void eraseWorkspaceScmMirror(long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            log.debug("Skipped SCM mirror erase: reason=workspaceNotFound, workspaceId={}", workspaceId);
            return;
        }

        // Derived rows first: practices observations/feedback and activity events point at the
        // artifacts about to be dropped, so they are erased while those artifacts still exist.
        eventPublisher.publishEvent(new ScmMirrorErasedEvent(workspaceId));

        List<String> monitoredNames = repositoryToMonitorRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();

        // Collection remove + orphanRemoval (never a bulk delete: purgeWorkspace step 5 clears the
        // same collection, and a bulk delete bypassing the persistence context would make
        // orphanRemoval re-issue DELETEs for already-deleted rows). saveAndFlush pushes the
        // monitor DELETEs to the DB BEFORE the orphan count query below reads them.
        workspace.getRepositoriesToMonitor().clear();
        workspaceRepository.saveAndFlush(workspace);

        int erased = 0;
        int shared = 0;
        for (String nameWithOwner : monitoredNames) {
            long remaining = repositoryToMonitorRepository.countByNameWithOwner(nameWithOwner);
            // Delegated cascade — the orphan guard lives there; this count is for the audit line only.
            repositoryMonitorService.deleteRepositoryIfOrphaned(nameWithOwner);
            if (remaining > 0) {
                shared++;
            } else {
                erased++;
            }
        }

        int teamsErased = eraseOrgTierIfLastWorkspace(workspace);

        // One consumer refresh for the whole erase rather than one per monitor: the workspace's
        // subject filter is rebuilt once, now matching nothing.
        if (natsProperties.enabled()) {
            natsConsumerService.ifAvailable(svc -> svc.updateScopeConsumer(workspaceId));
        }

        log.info(
            "scm.audit: revoke erase — actor={}, workspaceId={}, erasedRepositories={}, sharedRepositoriesSkipped={}, erasedTeams={}",
            LoggingUtils.sanitizeForLog(SecurityUtils.getCurrentUserLogin().orElse("system")),
            workspaceId,
            erased,
            shared,
            teamsErased
        );
    }

    /**
     * Org-tier mirror ({@code team}, {@code team_membership}, {@code organization_membership}) —
     * erased together with the workspace's binding to the {@link Organization} that keys them. These
     * tables key on the org, not on a repository, so the repository cascade cannot reach them; this
     * is their equivalent of {@code deleteRepositoryIfOrphaned}'s guard.
     *
     * <p><b>The organization binding is exclusive, not shared.</b> {@code Workspace.organization} is
     * a {@code @OneToOne} over a {@code unique = true} {@code organization_id} column (DB constraint
     * {@code workspace_organization_id_key}), so an organization backs at most ONE workspace. There
     * is therefore no "other tenant" that could still hold a lawful basis for this org's mirror: the
     * workspace being erased is always the last one, and the org-tier rows always go. The
     * {@code countOtherActiveWorkspacesForOrganization} guard below is defensive — it keeps this
     * method correct if that 1:1 mapping is ever relaxed — not a condition that fires today.
     *
     * <p>Contrast this with the repository tier, which genuinely IS shared: many workspaces may
     * monitor the same {@code repository}, which is why that tier needs a real orphan check.
     *
     * <p><b>Why the link is released.</b> A disconnect does not purge the workspace, so a workspace
     * that kept pointing at the org would occupy that organization's single binding slot forever —
     * no other workspace could ever install the same organization without violating the unique
     * constraint, and this workspace would be left pointing at an org whose mirror it just erased.
     * Releasing the link (and flushing, so the guard's {@code COUNT} reads the released state) frees
     * the organization for re-binding. That costs nothing on the reconnect path: the link is
     * re-provisioned on every install event — {@code GithubLifecycleListener#createOrUpdateFromInstallation}
     * re-resolves the org via {@code OrganizationService#upsertIdentity}, including the
     * reactivate-an-existing-workspace branch, and {@code GitLabWorkspaceInitializationService} does
     * the same.
     *
     * <p>The {@link Organization} row itself is never deleted — it is a global identity row reached
     * by {@code team} and {@code organization_membership} and by person-level identity resolution;
     * this method (like {@code WorkspaceLifecycleService#purgeWorkspace} step 7) merely unlinks it.
     * A workspace with no organization binding (a personal-account install) has no org-tier mirror
     * to erase.
     *
     * @return the number of teams erased (their memberships cascade via {@code Team.memberships})
     */
    private int eraseOrgTierIfLastWorkspace(Workspace workspace) {
        Organization organization = workspace.getOrganization();
        if (organization == null || StringUtils.isBlank(organization.getLogin())) {
            return 0;
        }

        workspace.setOrganization(null);
        workspaceRepository.saveAndFlush(workspace);

        // Defensive: the 1:1 organization binding means this count is always 0 today. Kept so the
        // method stays correct if that mapping is ever relaxed, and independent of this call order.
        long otherTenants = workspaceRepository.countOtherActiveWorkspacesForOrganization(
            organization.getId(),
            workspace.getId()
        );
        if (otherTenants > 0) {
            log.debug(
                "Skipped org-tier SCM erase: reason=organizationStillBound, organizationId={}, otherWorkspaces={}",
                organization.getId(),
                otherTenants
            );
            return 0;
        }

        Long providerId = organization.getProvider() == null ? null : organization.getProvider().getId();
        if (providerId == null) {
            return 0;
        }

        Set<Team> teams = new LinkedHashSet<>(
            teamRepository.findByOrganizationIgnoreCaseAndProviderId(organization.getLogin(), providerId)
        );
        // Entity deletes (not a bulk JPQL delete) so Team's CascadeType.REMOVE reaches team_membership.
        teamRepository.deleteAll(new ArrayList<>(teams));
        organizationMembershipRepository.deleteByOrganizationId(organization.getId());
        return teams.size();
    }
}

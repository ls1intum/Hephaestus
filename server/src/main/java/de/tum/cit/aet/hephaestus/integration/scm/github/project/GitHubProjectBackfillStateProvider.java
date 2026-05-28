package de.tum.cit.aet.hephaestus.integration.scm.github.project;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists backfill cursors and timestamps for GitHub Projects v2 sync.
 *
 * <p>Split out of the cross-module {@code BackfillStateProvider} SPI so the workspace
 * adapter no longer carries GitHub-specific persistence — projects are a GitHub-only
 * concept and live exclusively in the GitHub adapter. Direct dependency on
 * {@link ProjectRepository} is fine here because both consumer and provider sit inside
 * {@code integration/scm/github/project/}.
 *
 * <p>All methods are no-ops when the project no longer exists (deleted mid-sync); cursor
 * and timestamp writes are best-effort and reorder safely.
 */
@Component
public class GitHubProjectBackfillStateProvider {

    private final ProjectRepository projectRepository;

    public GitHubProjectBackfillStateProvider(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ── Item sync ──

    @Transactional
    public void updateProjectItemSyncCursor(Long projectId, String cursor) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setItemSyncCursor(cursor);
                projectRepository.save(project);
            });
    }

    @Transactional
    public void updateProjectItemsSyncedAt(Long projectId, Instant syncedAt) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setItemsSyncedAt(syncedAt);
                project.setItemSyncCursor(null);
                projectRepository.save(project);
            });
    }

    @Transactional(readOnly = true)
    public Optional<String> getProjectItemSyncCursor(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getItemSyncCursor);
    }

    @Transactional(readOnly = true)
    public Optional<Instant> getProjectItemsSyncedAt(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getItemsSyncedAt);
    }

    // ── Field sync ──

    @Transactional
    public void updateProjectFieldSyncCursor(Long projectId, String cursor) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setFieldSyncCursor(cursor);
                projectRepository.save(project);
            });
    }

    @Transactional
    public void updateProjectFieldsSyncedAt(Long projectId, Instant syncedAt) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setFieldsSyncedAt(syncedAt);
                project.setFieldSyncCursor(null);
                projectRepository.save(project);
            });
    }

    @Transactional(readOnly = true)
    public Optional<String> getProjectFieldSyncCursor(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getFieldSyncCursor);
    }

    @Transactional(readOnly = true)
    public Optional<Instant> getProjectFieldsSyncedAt(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getFieldsSyncedAt);
    }

    // ── Status update sync ──

    @Transactional
    public void updateProjectStatusUpdateSyncCursor(Long projectId, String cursor) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setStatusUpdateSyncCursor(cursor);
                projectRepository.save(project);
            });
    }

    @Transactional
    public void updateProjectStatusUpdatesSyncedAt(Long projectId, Instant syncedAt) {
        projectRepository
            .findById(projectId)
            .ifPresent(project -> {
                project.setStatusUpdatesSyncedAt(syncedAt);
                project.setStatusUpdateSyncCursor(null);
                projectRepository.save(project);
            });
    }

    @Transactional(readOnly = true)
    public Optional<String> getProjectStatusUpdateSyncCursor(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getStatusUpdateSyncCursor);
    }

    @Transactional(readOnly = true)
    public Optional<Instant> getProjectStatusUpdatesSyncedAt(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getStatusUpdatesSyncedAt);
    }
}

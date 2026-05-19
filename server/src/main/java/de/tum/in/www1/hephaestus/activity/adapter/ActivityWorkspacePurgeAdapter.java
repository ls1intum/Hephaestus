package de.tum.in.www1.hephaestus.activity.adapter;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.springframework.stereotype.Component;

/**
 * Adapter that handles activity event cleanup when a workspace is purged.
 *
 * <p>This contributor deletes all activity events associated with a workspace
 * during the purge operation. Activity events can be large in number, so this
 * uses a bulk delete query for efficiency.
 */
@Component
public class ActivityWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private final ActivityEventRepository activityEventRepository;

    public ActivityWorkspacePurgeAdapter(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        activityEventRepository.deleteAllByWorkspaceId(workspaceId);
    }

    @Override
    public int getOrder() {
        // Activity events should be deleted after workspace settings but before
        // repository monitors (which activity events may reference indirectly)
        return 100;
    }
}

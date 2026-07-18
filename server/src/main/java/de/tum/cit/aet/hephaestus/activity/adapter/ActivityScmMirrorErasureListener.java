package de.tum.cit.aet.hephaestus.activity.adapter;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases a workspace's {@code activity_event} rows when its SCM mirror is erased on
 * connection-disconnect or workspace-purge.
 *
 * <p>Activity events are 100% SCM-derived. Their {@code repository_id} FK is
 * {@code ON DELETE SET NULL}, so without this listener they would survive the repository cascade as
 * rows describing a mirror that no longer exists — the same "nothing ingested outlives the
 * connection" gap Slack and Outline already close. {@code ActivityWorkspacePurgeAdapter} covers the
 * purge trigger; this covers disconnect too, and the two are idempotent with respect to each other.
 *
 * <p>Runs synchronously inside the erasing transaction. The event indirection exists because
 * {@code activity} already depends on {@code workspace}, so a direct call from the eraser would
 * close a Spring Modulith cycle.
 */
@Component
public class ActivityScmMirrorErasureListener {

    private final ActivityEventRepository activityEventRepository;

    public ActivityScmMirrorErasureListener(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    @EventListener
    @Transactional
    public void onScmMirrorErased(ScmMirrorErasedEvent event) {
        activityEventRepository.deleteAllByWorkspaceId(event.workspaceId());
    }
}

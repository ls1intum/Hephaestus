package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class PracticesScmMirrorErasureListener {
    private final PracticesPrivacyRepository rows;
    private final FeedbackCopyCleanupCustody custody;
    private final ScanReportService reports;

    @EventListener
    @Transactional
    public void onScmMirrorErased(ScmMirrorErasedEvent event) {
        long ws = event.workspaceId();
        var ids = rows.scmResultIds(ws);
        if (ids.isEmpty()) return;
        var selected = ids.toArray(UUID[]::new);
        custody.requireAcknowledged(ws, rows.feedbackForResults(ws, selected));
        reports.eraseResultReferences(ws, ids);
        rows.deleteFeedbackForResults(ws, selected);
        rows.deleteResults(ws, selected);
    }
}

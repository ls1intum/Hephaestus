package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ConversationFeedbackErasureAdapter implements ConversationFeedbackErasure {
    private final PracticesPrivacyRepository rows;
    private final FeedbackCopyCleanupCustody custody;
    private final ScanReportService reports;

    @Override
    @Transactional
    public int eraseForThreads(long ws, Collection<Long> threads) {
        if (threads.isEmpty()) return 0;
        return erase(ws, rows.conversationResultIdsForThreads(ws, threads));
    }

    @Override
    @Transactional
    public int eraseAllConversationForWorkspace(long ws) {
        return erase(ws, rows.conversationResultIds(ws));
    }

    @Override
    @Transactional
    public int eraseConversationFeedbackAboutUser(long ws, long account) {
        return erase(ws, rows.conversationResultIdsForDeveloper(ws, account));
    }

    private int erase(long ws, List<UUID> ids) {
        if (ids.isEmpty()) return 0;
        var selected = ids.toArray(UUID[]::new);
        custody.requireAcknowledged(ws, rows.feedbackForResults(ws, selected));
        reports.eraseResultReferences(ws, ids);
        int feedback = rows.deleteFeedbackForResults(ws, selected);
        return feedback + rows.deleteResults(ws, selected);
    }
}

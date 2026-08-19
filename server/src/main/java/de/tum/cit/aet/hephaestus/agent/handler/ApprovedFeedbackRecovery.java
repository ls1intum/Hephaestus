package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.practices.feedback.approval.ApprovedFeedbackReadyEvent;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ApprovedFeedbackRecovery {

    private static final Logger log = LoggerFactory.getLogger(ApprovedFeedbackRecovery.class);
    private final FeedbackApprovalRepository approvalRepository;
    private final ApprovedFeedbackDeliveryListener delivery;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @SchedulerLock(name = "approved-feedback-recovery", lockAtMostFor = "PT1M", lockAtLeastFor = "PT5S")
    public void recover() {
        for (var pending : approvalRepository.findPendingApproved(PageRequest.of(0, 50))) {
            try {
                delivery.deliver(new ApprovedFeedbackReadyEvent(pending.getWorkspaceId(), pending.getFeedbackId()));
            } catch (RuntimeException exception) {
                log.warn("Approved feedback recovery deferred: feedbackId={}", pending.getFeedbackId(), exception);
            }
        }
    }
}

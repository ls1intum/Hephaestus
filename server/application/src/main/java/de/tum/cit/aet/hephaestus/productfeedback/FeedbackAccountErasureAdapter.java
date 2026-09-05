package de.tum.cit.aet.hephaestus.productfeedback;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class FeedbackAccountErasureAdapter implements AccountErasureContributor {
    private final SurveySubmissionRepository submissions;
    private final ProductFeedbackRepository feedback;

    @Override
    @Transactional
    public void eraseAccount(long accountId) {
        submissions.deleteAllByAccountId(accountId);
        feedback.deleteAllByAccountId(accountId);
    }
}

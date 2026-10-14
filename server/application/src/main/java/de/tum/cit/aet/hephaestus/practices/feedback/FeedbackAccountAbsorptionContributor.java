package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class FeedbackAccountAbsorptionContributor implements AccountAbsorptionContributor {

    private final FeedbackRepository feedback;
    private final FeedbackBasisRepository basis;

    @Override
    public void beforeAbsorb(long claimedAccountId, long unclaimedAccountId) {
        basis.deferDeveloperConstraints();
    }

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        feedback.absorbDeveloper(claimedAccountId, unclaimedAccountId);
        basis.absorbDeveloper(claimedAccountId, unclaimedAccountId);
    }

    @Override
    public void afterAbsorb(long claimedAccountId, long unclaimedAccountId) {
        basis.validateDeveloperConstraints();
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (feedback.countByDeveloperId(unclaimedAccountId) != 0 || basis.countByDeveloperId(unclaimedAccountId) != 0) {
            throw new IllegalStateException("Account absorption left feedback owned by the source account.");
        }
    }
}

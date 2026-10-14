package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackAccountAbsorptionContributor implements AccountAbsorptionContributor {

    private final FeedbackRepository feedback;
    private final FeedbackBasisRepository basis;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        feedback.absorbDeveloper(claimedAccountId, unclaimedAccountId);
        basis.absorbDeveloper(claimedAccountId, unclaimedAccountId);
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (feedback.countByDeveloperId(unclaimedAccountId) != 0 || basis.countByDeveloperId(unclaimedAccountId) != 0) {
            throw new IllegalStateException("Account absorption left feedback owned by the source account.");
        }
    }
}

package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ResultAccountAbsorptionContributor implements AccountAbsorptionContributor {
    private final PracticesPrivacyRepository rows;
    private final Clock clock;

    @Override
    @Transactional
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        rows.lockAccountReviews(claimedAccountId, unclaimedAccountId);
        rows.lockAccountResults(claimedAccountId, unclaimedAccountId);
        rows.endOverlappingClaims(claimedAccountId, unclaimedAccountId, clock.instant());
        rows.reassignResults(claimedAccountId, unclaimedAccountId);
        rows.reassignMembers(claimedAccountId, unclaimedAccountId);
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (rows.hasAccountResults(unclaimedAccountId))
            throw new IllegalStateException("Product attribution was not fully absorbed.");
    }
}

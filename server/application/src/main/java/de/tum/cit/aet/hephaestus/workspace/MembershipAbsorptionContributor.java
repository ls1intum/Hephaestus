package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnServerRole
@RequiredArgsConstructor
public class MembershipAbsorptionContributor implements AccountAbsorptionContributor {
    private final MembershipRepository memberships;

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        memberships.lockByAccountIds(List.of(claimedAccountId, unclaimedAccountId));
        memberships.absorbAccount(claimedAccountId, unclaimedAccountId);
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        memberships.deleteByAccountId(unclaimedAccountId);
    }
}

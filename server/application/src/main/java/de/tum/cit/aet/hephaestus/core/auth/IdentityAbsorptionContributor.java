package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@WorkspaceAgnostic("Identity ownership spans workspaces")
@RequiredArgsConstructor
public class IdentityAbsorptionContributor implements AccountAbsorptionContributor {
    private final IdentityRepository identities;
    private final AccountRepository accounts;

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        var target = accounts.getReferenceById(claimedAccountId);
        for (var identity : identities.findByAccount_Id(unclaimedAccountId)) {
            identity.setAccount(target);
            identities.save(identity);
        }
        identities.flush();
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (!identities.findByAccount_Id(unclaimedAccountId).isEmpty())
            throw new IllegalStateException("Source identity ownership remains after absorption");
    }
}

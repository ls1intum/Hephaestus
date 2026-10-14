package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnServerRole
@WorkspaceAgnostic("Verified unclaimed account absorption spans workspaces")
@RequiredArgsConstructor
public class AccountAbsorptionService {
    private final AccountRepository accounts;
    private final IdentityRepository identities;
    private final PersonDataWriteFence writeFence;
    private final IdentityKeyLock identityKeys;
    private final List<AccountAbsorptionContributor> contributors;
    private final JdbcTemplate jdbc;

    @Transactional
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        if (claimedAccountId == unclaimedAccountId) return;
        var sourceIdentities = identities.findByAccount_Id(unclaimedAccountId);
        if (sourceIdentities.isEmpty()) throw conflict("The unclaimed account has no identity.");
        var sourceKeys = sourceIdentities.stream()
                .map(i -> new PersonIdentity(
                        i.getProviderId(),
                        i.getProviderUserKey(),
                        i.getTeamKey().isEmpty() ? null : i.getTeamKey()))
                .toList();
        if (!writeFence.holdForWrite(sourceKeys)) throw conflict("This identity is being erased.");
        identityKeys.lock(sourceKeys);
        var first = accounts.findByIdForUpdate(Math.min(claimedAccountId, unclaimedAccountId))
                .orElseThrow(() -> conflict("This account is unavailable."));
        var second = accounts.findByIdForUpdate(Math.max(claimedAccountId, unclaimedAccountId))
                .orElseThrow(() -> conflict("This account is unavailable."));
        var target = claimedAccountId < unclaimedAccountId ? first : second;
        var source = claimedAccountId < unclaimedAccountId ? second : first;
        if (source.getStatus() != Account.Status.UNCLAIMED
                || (target.getStatus() != Account.Status.ACTIVE && target.getStatus() != Account.Status.SUSPENDED))
            throw conflict("Only an unclaimed account can be absorbed by its verified owner.");
        var ordered = contributors.stream()
                .sorted(Comparator.comparingInt(AccountAbsorptionContributor::order))
                .toList();
        jdbc.execute("SET CONSTRAINTS"
                + " sfk_feedback_basis_result_person,sfk_feedback_basis_feedback_person"
                + " DEFERRED");
        for (var contributor : ordered) contributor.absorb(claimedAccountId, unclaimedAccountId);
        accounts.flush();
        jdbc.execute("SET CONSTRAINTS"
                + " sfk_feedback_basis_result_person,sfk_feedback_basis_feedback_person"
                + " IMMEDIATE");
        for (var contributor : ordered.reversed()) contributor.finish(claimedAccountId, unclaimedAccountId);
        target.setFeedbackDeliveryEnabled(target.isFeedbackDeliveryEnabled() && source.isFeedbackDeliveryEnabled());
        target.setPublicActivityVisible(target.isPublicActivityVisible() && source.isPublicActivityVisible());
        var sourceChoiceAt = source.getAiChoiceAt();
        var targetChoiceAt = target.getAiChoiceAt();
        if (sourceChoiceAt != null && (targetChoiceAt == null || sourceChoiceAt.isAfter(targetChoiceAt))) {
            target.setAiChoice(source.getAiChoice());
            target.setAiChoiceAt(sourceChoiceAt);
        }
        accounts.save(target);
        accounts.delete(source);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

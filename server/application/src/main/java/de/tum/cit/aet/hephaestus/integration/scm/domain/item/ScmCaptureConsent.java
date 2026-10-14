package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Checks every selected contributor, independently of the practice's About role. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScmCaptureConsent {
    private final PersonIdentityResolver identities;
    private final AccountDirectory accounts;
    private final PersonProcessingSuppression suppression;
    private final PersonDataCopyRecorder copies;

    public void require(Collection<User> contributors) {
        for (User user : contributors) {
            var provider = user.getProvider();
            Long providerId = provider.getId();
            if (providerId == null)
                throw new ItemCaptureException(
                        ItemCaptureCode.CAPTURE_FAILED, "Contributor provider identity is unavailable");
            String key = Long.toString(user.getNativeId());
            if (suppression.isSuppressed(providerId, key, null)) throw withheld();
            Optional<Long> accountId;
            try {
                accountId = identities.accountFor(new PersonIdentity(providerId, key, null));
            } catch (ResponseStatusException conflict) {
                if (conflict.getStatusCode().value() == HttpStatus.CONFLICT.value()) throw withheld();
                throw conflict;
            }
            if (accountId.isPresent()) {
                var account = accounts.find(accountId.orElseThrow()).orElseThrow(ScmCaptureConsent::withheld);
                if (suppression.isAccountSuppressed(account.id())
                        || account.aiChoice() == AiChoice.NO_AI
                        || (account.status() != Account.Status.ACTIVE && account.status() != Account.Status.UNCLAIMED))
                    throw withheld();
            }
            copies.recordIdentity(
                    new PersonCopyIdentity(provider.getType().name(), provider.getServerUrl(), key, null));
        }
    }

    private static ItemCaptureException withheld() {
        return new ItemCaptureException(
                ItemCaptureCode.CONSENT_WITHHELD, "A contributor has withheld permission to process this work");
    }
}

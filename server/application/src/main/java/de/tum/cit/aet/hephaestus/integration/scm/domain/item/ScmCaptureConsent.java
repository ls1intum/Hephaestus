package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks every selected contributor, independently of the practice's About role. */
@Component
@RequiredArgsConstructor
public class ScmCaptureConsent {
    private final AccountIdentityQuery identities;
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
            var account = identities.resolveAccountId(providerId, key, null).flatMap(accounts::find);
            if (account.filter(a -> suppression.isAccountSuppressed(a.id())
                            || a.aiChoice() == AiChoice.NO_AI
                            || (a.status() != Account.Status.ACTIVE && a.status() != Account.Status.UNCLAIMED))
                    .isPresent()) throw withheld();
            copies.recordIdentity(
                    new PersonCopyIdentity(provider.getType().name(), provider.getServerUrl(), key, null));
        }
    }

    private static ItemCaptureException withheld() {
        return new ItemCaptureException(
                ItemCaptureCode.CONSENT_WITHHELD, "A contributor has withheld permission to process this work");
    }
}

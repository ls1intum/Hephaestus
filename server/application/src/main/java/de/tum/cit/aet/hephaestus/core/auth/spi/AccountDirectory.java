package de.tum.cit.aet.hephaestus.core.auth.spi;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface AccountDirectory {
    Optional<AccountView> find(long accountId);

    Map<Long, AccountView> findAll(Collection<Long> accountIds);

    void setFeedbackDeliveryEnabled(long accountId, boolean enabled);

    void setAiChoice(long accountId, @Nullable AiChoice choice);

    record AccountView(
            long id,
            Account.Status status,
            String displayName,
            @Nullable AiChoice aiChoice,
            @Nullable Instant aiChoiceAt,
            boolean feedbackDeliveryEnabled) {}
}

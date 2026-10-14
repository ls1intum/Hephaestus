package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@WorkspaceAgnostic("Account status and choices span all workspaces")
@RequiredArgsConstructor
public class AccountDirectoryService implements AccountDirectory {
    private final AccountRepository accounts;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> find(long accountId) {
        return accounts.findById(accountId).map(AccountDirectoryService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AccountView> findAll(Collection<Long> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .map(AccountDirectoryService::view)
                .collect(Collectors.toUnmodifiableMap(AccountView::id, Function.identity()));
    }

    @Override
    @Transactional
    public void setFeedbackDeliveryEnabled(long accountId, boolean enabled) {
        var account = accounts.findByIdForUpdate(accountId).orElseThrow();
        if (account.getStatus() != Account.Status.ACTIVE)
            throw new IllegalStateException("Only an active account can choose feedback delivery");
        account.setFeedbackDeliveryEnabled(enabled);
        accounts.save(account);
    }

    @Override
    @Transactional
    public void setAiChoice(long accountId, @Nullable AiChoice choice) {
        var account = accounts.findByIdForUpdate(accountId).orElseThrow();
        if (account.getStatus() != Account.Status.ACTIVE)
            throw new IllegalStateException("Only an active account can choose AI use");
        account.setAiChoice(choice);
        account.setAiChoiceAt(choice == null ? null : clock.instant());
        accounts.save(account);
    }

    private static AccountView view(Account account) {
        return new AccountView(
                Objects.requireNonNull(account.getId()),
                account.getStatus(),
                account.getDisplayName(),
                account.getAiChoice(),
                account.getAiChoiceAt(),
                account.isFeedbackDeliveryEnabled());
    }
}

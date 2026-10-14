package de.tum.cit.aet.hephaestus.account.userview;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipPageQuery;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ViewedAccountService {
    private final MembershipPageQuery memberships;
    private final AccountReferenceService accounts;

    public Page<UserViewAccountDTO> list(Long workspaceId, Pageable pageable) {
        var page = memberships.viewableAccountIds(workspaceId, pageable);
        var references = accounts.references(workspaceId, page.getContent());
        return page.map(accountId -> describe(Objects.requireNonNull(references.get(accountId))));
    }

    public UserViewAccountDTO requireAccount(Long workspaceId, Long accountId) {
        if (!memberships.isCurrentMember(workspaceId, accountId))
            throw new EntityNotFoundException("Account", accountId);
        return describe(accounts.reference(workspaceId, accountId));
    }

    private UserViewAccountDTO describe(AccountRefDTO account) {
        String login = account.identities().stream()
                .map(ProviderIdentityRefDTO::login)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new UserViewAccountDTO(account.id(), account, login);
    }
}

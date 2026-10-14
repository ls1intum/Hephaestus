package de.tum.cit.aet.hephaestus.account.userview;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ViewedAccountService {
    private final MembershipRepository memberships;
    private final AccountReferenceService accounts;
    private final AccountDirectory directory;

    public Page<UserViewAccountDTO> list(Long workspaceId, Pageable pageable) {
        var candidates = memberships.findByWorkspace_Id(workspaceId).stream()
                .filter(member -> member.getLeftAt() == null)
                .toList();
        var accountViews = directory.findAll(candidates.stream()
                .map(member -> Objects.requireNonNull(member.getAccountId()))
                .toList());
        var visible = candidates.stream()
                .filter(member -> {
                    var account = accountViews.get(member.getAccountId());
                    return account != null
                            && (account.status() == Account.Status.ACTIVE
                                    || account.status() == Account.Status.UNCLAIMED
                                    || account.status() == Account.Status.SUSPENDED);
                })
                .map(member -> describe(workspaceId, member))
                .sorted(Comparator.comparing(
                        view -> Objects.requireNonNullElse(view.account().displayName(), "")))
                .toList();
        return new PageImpl<>(
                visible.stream()
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .toList(),
                pageable,
                visible.size());
    }

    public UserViewAccountDTO requireAccount(Long workspaceId, Long accountId) {
        return memberships
                .findByWorkspace_IdAndAccount_Id(workspaceId, accountId)
                .filter(member -> member.getLeftAt() == null)
                .map(member -> describe(workspaceId, member))
                .orElseThrow(() -> new EntityNotFoundException("Account", accountId));
    }

    private UserViewAccountDTO describe(long workspaceId, Membership member) {
        var account = accounts.reference(workspaceId, Objects.requireNonNull(member.getAccountId()));
        String login = account.identities().stream()
                .map(ProviderIdentityRefDTO::login)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new UserViewAccountDTO(account.id(), account, login);
    }
}

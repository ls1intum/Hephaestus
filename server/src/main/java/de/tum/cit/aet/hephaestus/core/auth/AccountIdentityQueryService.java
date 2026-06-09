package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-module implementation of {@link AccountIdentityQuery}. Lives in {@code core.auth} so it can
 * touch the {@code IdentityLink} domain entity directly; exposes only the narrow vendor-neutral
 * SPI to {@code integration}.
 *
 * @see AccountIdentityQuery for the {@code sub → Account → IdentityLink} provisioning rationale.
 */
@Service
@WorkspaceAgnostic("Identity links are user-scoped (account → IdentityLink)")
public class AccountIdentityQueryService implements AccountIdentityQuery {

    private final IdentityLinkRepository identityLinkRepository;

    public AccountIdentityQueryService(IdentityLinkRepository identityLinkRepository) {
        this.identityLinkRepository = identityLinkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityLinkView> activeLinksForAccount(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        return identityLinkRepository.findActiveByAccountId(accountId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public void linkExternalActor(Long identityLinkId, Long externalActorId) {
        if (identityLinkId == null || externalActorId == null) {
            return;
        }
        identityLinkRepository.linkExternalActorIfAbsent(identityLinkId, externalActorId);
    }

    private IdentityLinkView toView(IdentityLink link) {
        return new IdentityLinkView(
            link.getId(),
            link.getGitProviderId(),
            link.getSubject(),
            link.getUsernameAtSignup(),
            link.getDisplayName(),
            link.getAvatarUrl(),
            link.getProfileUrl(),
            link.getExternalActorId()
        );
    }
}

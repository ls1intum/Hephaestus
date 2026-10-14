package de.tum.cit.aet.hephaestus.integration.scm.identity;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery.IdentityView;
import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resolves linked SCM identities from the current provider mirror and its immutable native key. */
@Service
public class AuthenticatedGitProviderUserService {

    private final UserRepository userRepository;
    private final IdentityProviderRepository gitProviderRepository;
    private final AccountIdentityQuery accountIdentityQuery;

    public AuthenticatedGitProviderUserService(
            UserRepository userRepository,
            IdentityProviderRepository gitProviderRepository,
            AccountIdentityQuery accountIdentityQuery) {
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.accountIdentityQuery = accountIdentityQuery;
    }

    /** Resolves the first linked GitHub/GitLab identity. */
    @Transactional
    public Optional<User> resolveCurrentUser() {
        for (IdentityView link : activeLinksForCurrentAccount()) {
            Optional<IdentityProvider> provider = gitProviderRepository.findById(link.providerId());
            if (provider.isPresent()
                    && (provider.get().getType() == IdentityProviderType.GITHUB
                            || provider.get().getType() == IdentityProviderType.GITLAB)) {
                return Optional.of(resolveUser(link));
            }
        }
        return Optional.empty();
    }

    /**
     * The account's synchronized GitLab profile on the instance at {@code serverUrl}. A link to
     * another GitLab instance, or to GitHub, never stands in for it. Without exactly one matching
     * link this is 409: identity providers recorded before instances were compared by origin can
     * name one instance twice, and neither link may be picked.
     */
    @Transactional
    public User resolveCurrentGitLabUser(String serverUrl) {
        Optional<String> instance = ScmOrigin.of(serverUrl);
        List<IdentityView> matching = activeLinksForCurrentAccount().stream()
                .filter(link -> instance.isPresent()
                        && gitProviderRepository
                                .findById(link.providerId())
                                .filter(provider -> provider.getType() == IdentityProviderType.GITLAB)
                                .map(provider -> instance.equals(ScmOrigin.of(provider.getServerUrl())))
                                .orElse(false))
                .toList();
        if (matching.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your account has more than one GitLab identity on "
                            + serverUrl
                            + ". Disconnect the one you do not use in User settings, under"
                            + " Connected accounts.");
        }
        if (matching.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Connect your GitLab account on "
                            + serverUrl
                            + " before you create a workspace there. Do this in User settings,"
                            + " under Connected accounts.");
        }
        return resolveUser(matching.getFirst());
    }

    private List<IdentityView> activeLinksForCurrentAccount() {
        return SecurityUtils.getCurrentAccountId()
                .map(accountIdentityQuery::activeLinksForAccount)
                .orElseGet(List::of);
    }

    /** Returns the current profile matching the verified provider key. */
    private User resolveUser(IdentityView link) {
        IdentityProvider provider = gitProviderRepository
                .findById(link.providerId())
                .orElseThrow(() -> new IllegalStateException("We could not find the provider for this connected"
                        + " account. Disconnect it in User settings,"
                        + " then connect it again."));
        long nativeId = parseSubject(link.providerUserKey(), provider.getType());
        return userRepository
                .findByNativeIdAndProviderId(nativeId, link.providerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Your provider profile is not available yet. Wait for" + " synchronization, then try again."));
    }

    private static long parseSubject(String subject, IdentityProviderType type) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            // A mutable login cannot substitute for the provider's numeric actor id.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hephaestus cannot use your connected "
                            + type
                            + " account because its ID is not a number. Disconnect it in User"
                            + " settings, under Connected accounts, then connect it again.",
                    e);
        }
    }
}

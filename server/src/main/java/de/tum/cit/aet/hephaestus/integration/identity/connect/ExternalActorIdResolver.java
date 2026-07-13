package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.ExternalActorQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration-side implementation of the {@link ExternalActorQuery} auth SPI: resolves the synced SCM
 * {@code User} for a git identity so {@code core.auth} can bind {@code identity_link.external_actor_id}
 * at JIT account creation without importing the {@code User} entity.
 *
 * <p>Lookup order mirrors the read-side fallback in {@code workspace.CurrentAccountUsers}: the
 * provider-native id (the OAuth {@code subject} for GitHub/GitLab, immutable) wins over the login
 * (mutable — a rename between sync and first login would mismatch). The login fallback is an EXACT
 * case-insensitive match and binds only a UNIQUE match: because the bind is persisted onto
 * {@code identity_link.external_actor_id}, an ambiguous login must resolve to nothing rather than risk
 * attaching someone else's actor to the account.
 */
@Component
public class ExternalActorIdResolver implements ExternalActorQuery {

    private final UserRepository userRepository;

    public ExternalActorIdResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findExternalActorId(long gitProviderId, String subject, @Nullable String username) {
        Optional<Long> byNativeId = parseNativeId(subject).flatMap(nativeId ->
            userRepository.findByNativeIdAndProviderId(nativeId, gitProviderId).map(User::getId)
        );
        if (byNativeId.isPresent()) {
            return byNativeId;
        }
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<User> byLogin = userRepository.findAllByExactLoginAndProviderId(username, gitProviderId);
        // Only a unique match may bind; ambiguity (however it arose) resolves to "no match" so the login
        // flow proceeds with an unbound link instead of persisting a possibly-wrong actor or throwing.
        return byLogin.size() == 1 ? Optional.of(byLogin.get(0).getId()) : Optional.empty();
    }

    private static Optional<Long> parseNativeId(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

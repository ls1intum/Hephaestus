package de.tum.cit.aet.hephaestus.integration.identity.connect;

import de.tum.cit.aet.hephaestus.core.auth.spi.ExternalActorQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration-side implementation of {@link ExternalActorQuery}: answers {@code core.auth}'s two questions
 * about the synced SCM {@code User} mirror without exposing the {@code User} entity across the boundary.
 *
 * <p>Lookup order for the signup bind mirrors the read-side fallback in {@code workspace.CurrentAccountUsers}:
 * the provider-native id (the OAuth {@code subject} for GitHub and GitLab, immutable) beats the login
 * (mutable — a rename between sync and first login would mismatch).
 *
 * <p>The login fallback is an exact, case-insensitive match that binds ONLY a unique result. Because the bind
 * is persisted and never re-evaluated, an ambiguous login must resolve to nothing: attaching the wrong actor
 * would show one person another person's practice feedback, and no later pass would notice.
 */
@Component
@RequiredArgsConstructor
public class ExternalActorIdResolver implements ExternalActorQuery {

    private final UserRepository userRepository;

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
        return byLogin.size() == 1 ? Optional.of(byLogin.get(0).getId()) : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> loginsByActorIds(Collection<Long> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> logins = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(actorIds)) {
            if (user.getId() != null && user.getLogin() != null) {
                logins.put(user.getId(), user.getLogin());
            }
        }
        return logins;
    }

    /** The subject is the provider-native numeric user id for GitHub and GitLab; anything else is not one. */
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

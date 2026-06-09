package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-{@code account}-module implementation of {@link AccountPreferencesQuery}. Reads
 * {@code user_preferences} (keyed by SCM login) and exposes only the narrow auth-spi view to
 * {@code core.auth}'s GDPR data-export (dependency inversion — interface owned by {@code
 * core.auth}, implementation by the data owner). Read-only; never provisions a row.
 */
@Service
@WorkspaceAgnostic("User-scoped preferences read for GDPR export — not workspace-specific")
public class AccountPreferencesQueryAdapter implements AccountPreferencesQuery {

    private static final Logger log = LoggerFactory.getLogger(AccountPreferencesQueryAdapter.class);

    private final UserPreferencesRepository userPreferencesRepository;

    public AccountPreferencesQueryAdapter(UserPreferencesRepository userPreferencesRepository) {
        this.userPreferencesRepository = userPreferencesRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferencesView> preferencesForLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        try {
            return userPreferencesRepository
                .findByUserLogin(login)
                .map(p -> new PreferencesView(p.isParticipateInResearch(), p.isAiReviewEnabled()));
        } catch (RuntimeException e) {
            log.error("auth.export: preferences lookup failed for login", e);
            return Optional.empty();
        }
    }
}

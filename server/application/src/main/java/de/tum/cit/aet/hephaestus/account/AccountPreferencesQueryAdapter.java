package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@WorkspaceAgnostic("User-scoped account preferences are not workspace-specific")
public class AccountPreferencesQueryAdapter implements AccountPreferencesQuery {

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
        return userPreferencesRepository
            .findByUserLogin(login)
            .map(p -> new PreferencesView(p.isParticipateInResearch(), p.isPracticeFeedbackDeliveryEnabled()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferencesView> preferencesForUserId(long userId) {
        return userPreferencesRepository
            .findByUserId(userId)
            .map(p -> new PreferencesView(p.isParticipateInResearch(), p.isPracticeFeedbackDeliveryEnabled()));
    }
}

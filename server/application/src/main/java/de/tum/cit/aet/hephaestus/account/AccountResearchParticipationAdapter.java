package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationCommand;
import org.springframework.stereotype.Service;

/**
 * In-{@code account}-module implementation of {@link ResearchParticipationCommand}. The cross-module write
 * port is owned by {@code core.auth} and consumed by modules that surface a consent control outside the webapp
 * (notably the Slack App Home toggle in {@code integration.slack}); binding it here — by the data owner —
 * keeps neither module importing the other's domain types. Mirrors the sibling read-only
 * {@link AccountPreferencesQueryAdapter}: a thin SPI seam that delegates the lenient consent write to
 * {@link AccountPreferencesService#setForLogin}, which owns the {@code user_preferences} row + analytics revocation.
 */
@Service
@WorkspaceAgnostic("User-scoped research-consent write — not workspace-specific")
public class AccountResearchParticipationAdapter implements ResearchParticipationCommand {

    private final AccountPreferencesService accountPreferencesService;

    public AccountResearchParticipationAdapter(AccountPreferencesService accountPreferencesService) {
        this.accountPreferencesService = accountPreferencesService;
    }

    @Override
    public void setForLogin(String login, boolean participate, ConsentSource source) {
        accountPreferencesService.setForLogin(login, participate, source);
    }
}

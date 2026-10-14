package de.tum.cit.aet.hephaestus.account;

import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.security.UserViewContextHolder;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AccountCurrentDeveloperAdapter implements CurrentDeveloperLookup {
    @Override
    public Optional<Long> currentDeveloperId() {
        var view = UserViewContextHolder.get();
        return view == null ? SecurityUtils.getCurrentAccountId() : Optional.of(view.accountId());
    }

    @Override
    public long currentDeveloperIdElseThrow() {
        return currentDeveloperId().orElseThrow();
    }
}

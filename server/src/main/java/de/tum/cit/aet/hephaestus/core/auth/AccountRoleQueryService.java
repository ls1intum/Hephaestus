package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRoleQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-module implementation of {@link AccountRoleQuery}. Lives in {@code core.auth} so it can
 * touch the {@code domain} entities directly; exposes only the narrow SPI to other modules.
 */
@Service
@WorkspaceAgnostic("Role/feature lookups are user-scoped (login → account → account_feature)")
public class AccountRoleQueryService implements AccountRoleQuery {

    private static final Logger log = LoggerFactory.getLogger(AccountRoleQueryService.class);

    private final AccountFeatureRepository accountFeatureRepository;

    public AccountRoleQueryService(AccountFeatureRepository accountFeatureRepository) {
        this.accountFeatureRepository = accountFeatureRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeatureFlag(String login, String flag) {
        if (login == null || flag == null || flag.isBlank()) {
            return false;
        }
        try {
            return accountFeatureRepository.existsActiveFeatureForLogin(login, flag);
        } catch (RuntimeException e) {
            log.error("auth.role: feature-flag check failed for login={}, flag={}", login, flag, e);
            return false; // fail-closed
        }
    }
}

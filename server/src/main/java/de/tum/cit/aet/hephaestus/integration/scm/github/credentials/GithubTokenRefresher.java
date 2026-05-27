package de.tum.cit.aet.hephaestus.integration.scm.github.credentials;

import de.tum.cit.aet.hephaestus.integration.scm.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.github.app.GitHubAppTokenService.InstallationToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.GithubAppCredential;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.TokenRefresher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link TokenRefresher}. Delegates GitHub App installation token
 * minting to the existing {@link GitHubAppTokenService} (JWT signer + caffeine token
 * cache); the legacy service already enforces the &lt;10-minute JWT window, &gt;60s
 * cache margin, and 403/404 handling, so we keep it as the single source of truth.
 *
 * <p>{@link GitHubAppTokenService} is wired with {@code required=false} so this bean
 * stays satisfiable during the C13 migration window (the legacy service is
 * configuration-gated by GitHub App credentials being present — in dev / test
 * environments without a configured App, the bean is absent and refresh attempts
 * surface as {@link IllegalStateException}). PAT-backed Connections do not flow
 * through here; GitHub has no OAuth refresh path either, so non-App sources throw
 * {@link UnsupportedOperationException} rather than silently returning stale tokens.
 */
@Component
public class GithubTokenRefresher implements TokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(GithubTokenRefresher.class);

    @Nullable
    private final GitHubAppTokenService appTokenService;

    @Autowired
    public GithubTokenRefresher(@Autowired(required = false) @Nullable GitHubAppTokenService appTokenService) {
        this.appTokenService = appTokenService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public BearerToken refresh(IntegrationRef ref, CredentialBundle source) {
        if (!(source instanceof GithubAppCredential app)) {
            throw new UnsupportedOperationException(
                "GitHub TokenRefresher only supports GithubAppCredential, got " +
                    (source == null ? "null" : source.getClass().getSimpleName())
            );
        }
        if (appTokenService == null) {
            throw new IllegalStateException(
                "GitHubAppTokenService is not wired — cannot mint installation token for installationId=" +
                    app.installationId()
            );
        }
        InstallationToken minted = appTokenService.getInstallationTokenDetails(app.installationId());
        log.debug(
            "Minted GitHub installation token for workspace={} installationId={} expiresAt={}",
            ref == null ? null : ref.workspaceId(),
            app.installationId(),
            minted.expiresAt()
        );
        return new BearerToken(minted.token(), minted.expiresAt());
    }
}

package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.OAuthClientSecret;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base strategy for workspace-scoped OIDC login providers (a workspace bringing its own
 * self-hosted GitLab / GHE OAuth app). Inline-credentials flow: the admin pastes
 * {@code issuer_url}, {@code client_id}, {@code client_secret}, {@code scopes},
 * {@code display_name}.
 *
 * <p>{@link #initiate} runs the {@link IssuerDiscoveryProbe} against the issuer URL
 * BEFORE returning the credential bundle — the SSRF / discovery validation is the whole
 * point. On success it yields an {@link ConnectInitiation.AcceptInline} carrying an
 * {@link OAuthClientSecret} bundle; the host of the issuer URL becomes the Connection's
 * {@code instanceKey} so the {@code (workspace, kind, instance_key)} uniqueness allows one
 * provider per host.
 *
 * <p>Two concrete subclasses bind the two {@link IntegrationKind} values — the
 * {@code ConnectionController} resolves strategies 1:1 by kind.
 *
 * <p>{@link #finalizeConnect} is not applicable (the OAuth dance happens at user-login
 * time via {@code oauth2Login}, not at registration). {@link #revoke} is a no-op (no
 * vendor-side state — we only stored an app credential, which the admin rotates on the
 * IdP side).
 */
public abstract class OidcLoginConnectionStrategy implements ConnectionStrategy {

    private static final Logger log = LoggerFactory.getLogger(OidcLoginConnectionStrategy.class);

    static final String INPUT_ISSUER_URL = "issuer_url";
    static final String INPUT_CLIENT_ID = "client_id";
    static final String INPUT_CLIENT_SECRET = "client_secret";

    private final IssuerDiscoveryProbe probe;

    protected OidcLoginConnectionStrategy(IssuerDiscoveryProbe probe) {
        this.probe = probe;
    }

    @Override
    public ConnectInitiation initiate(InitiateRequest request) {
        Map<String, String> input = request.userInput();
        if (input == null) {
            throw new IllegalArgumentException(
                "OIDC login initiate requires userInput with 'issuer_url', 'client_id', 'client_secret'"
            );
        }
        String issuerUrl = require(input, INPUT_ISSUER_URL);
        String clientId = require(input, INPUT_CLIENT_ID);
        String clientSecret = require(input, INPUT_CLIENT_SECRET);

        // SSRF-protected discovery probe — throws IssuerValidationException (→ 400 at the
        // controller) if the issuer points anywhere non-public or the discovery doc is
        // malformed. Validates BEFORE we persist the secret.
        IssuerDiscoveryProbe.DiscoveryResult discovery = probe.validate(issuerUrl);
        log.info(
            "OIDC login provider validated: kind={} workspace={} issuer={} authEndpoint={}",
            kind(),
            request.workspaceId(),
            discovery.issuer(),
            discovery.authorizationEndpoint()
        );

        CredentialBundle bundle = new OAuthClientSecret(clientId, clientSecret);
        String instanceKey = hostOf(issuerUrl);
        return new ConnectInitiation.AcceptInline(bundle, instanceKey);
    }

    @Override
    public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
        // The end-user OAuth dance is handled by oauth2Login at login time, not here.
        return new ConnectFinalization.Failed(
            "OIDC login registration uses inline credentials — finalizeConnect is not applicable"
        );
    }

    @Override
    public void revoke(IntegrationRef ref) {
        // No vendor-side state: we only hold an app client_secret. The admin rotates /
        // deletes the OAuth app on the IdP side. The Connection state transition to
        // UNINSTALLED is performed by the orchestrator.
        log.info(
            "OIDC login revoke called for kind={} workspace={} instanceKey={} (no vendor-side revoke)",
            kind(),
            ref == null ? null : ref.workspaceId(),
            ref == null ? null : ref.instanceKey()
        );
    }

    private static String require(Map<String, String> input, String key) {
        String value = input.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: '" + key + "'");
        }
        return value;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("issuer_url has no host");
            }
            return host;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("issuer_url is not a valid URL");
        }
    }
}

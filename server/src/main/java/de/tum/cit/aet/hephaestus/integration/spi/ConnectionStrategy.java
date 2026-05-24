package de.tum.cit.aet.hephaestus.integration.spi;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import java.net.URI;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Per-kind connection lifecycle strategy.
 *
 * <p>Drives the user-visible "Connect X" + "Disconnect X" flows. Vendor specifics
 * (GitHub App install click vs GitLab PAT paste vs Slack OAuth vs Outline OAuth)
 * live in per-kind implementations; the orchestrator stays vendor-neutral.
 */
public interface ConnectionStrategy {

    IntegrationKind kind();

    /**
     * Begin connecting. Returns either a redirect to the vendor (OAuth / App install)
     * or an inline-credentials prompt (paste a PAT).
     */
    ConnectInitiation initiate(InitiateRequest request);

    /**
     * Complete a redirect-style flow: the vendor sent the user back via
     * callback with code/state/etc.
     */
    ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams);

    /**
     * Probe credentials freshly — fail-fast on revoked tokens before going ACTIVE.
     * Called from {@link #finalizeConnect}, periodic health checks, and the admin
     * "Test connection" button.
     */
    ValidationResult validate(IntegrationRef ref, CredentialBundle credentials);

    /** Revoke vendor-side (best-effort) and signal local state change. */
    void revoke(IntegrationRef ref);

    record InitiateRequest(
        long workspaceId,
        IntegrationKind kind,
        Map<String, String> userInput,        // pasted PAT, configured server URL, etc.
        @Nullable URI redirectAfter           // where to bounce the user post-OAuth
    ) {
    }

    sealed interface ConnectInitiation
        permits ConnectInitiation.RedirectToVendor, ConnectInitiation.AcceptInline {

        record RedirectToVendor(URI vendorUrl, String oauthState) implements ConnectInitiation {}

        record AcceptInline(CredentialBundle credentials, @Nullable String instanceKey) implements ConnectInitiation {}
    }

    sealed interface ConnectFinalization
        permits ConnectFinalization.Completed, ConnectFinalization.Failed {

        record Completed(String instanceKey, CredentialBundle credentials, @Nullable String displayName)
            implements ConnectFinalization {
        }

        record Failed(String reason) implements ConnectFinalization {}
    }

    sealed interface ValidationResult
        permits ValidationResult.Ok, ValidationResult.Failed, ValidationResult.NotImplemented {
        record Ok(@Nullable String observedInstanceKey, @Nullable String observedDisplayName) implements ValidationResult {}
        record Failed(String reason) implements ValidationResult {}
        /**
         * Honest signal that the strategy has no vendor-side probe yet. Callers MUST treat
         * this differently from {@link Ok} — typically: log + skip, never auto-transition
         * a Connection to ACTIVE on this verdict alone.
         */
        record NotImplemented(String reason) implements ValidationResult {}
    }
}

package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.spi.ApprovalChannel;
import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectParser;
import de.tum.cit.aet.hephaestus.integration.spi.TokenRefresher;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup-time validation that every declared {@link Capability} has its required
 * per-kind SPI beans wired. Throws so misconfigurations surface immediately, not at
 * the first request. Gated to the application-server runtime role since worker pods
 * intentionally wire only a subset of SPI beans.
 */
@Component
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class IntegrationFrameworkBootstrap {

    private static final Logger log = LoggerFactory.getLogger(IntegrationFrameworkBootstrap.class);

    private final IntegrationManifestRegistry manifests;
    private final List<WebhookSignatureVerifier> signatureVerifiers;
    private final List<WebhookSecretSource> secretSources;
    private final List<SubjectKeyDeriver> subjectKeyDerivers;
    private final List<SubjectParser> subjectParsers;
    private final List<ApiCredentialProvider> credentialProviders;
    private final List<TokenRefresher> tokenRefreshers;
    private final List<FeedbackChannel> feedbackChannels;
    private final List<InlineFindingChannel> inlineFindingChannels;
    private final List<ApprovalChannel> approvalChannels;
    private final List<IntegrationLifecycleListener> lifecycleListeners;

    public IntegrationFrameworkBootstrap(
        IntegrationManifestRegistry manifests,
        List<WebhookSignatureVerifier> signatureVerifiers,
        List<WebhookSecretSource> secretSources,
        List<SubjectKeyDeriver> subjectKeyDerivers,
        List<SubjectParser> subjectParsers,
        List<ApiCredentialProvider> credentialProviders,
        List<TokenRefresher> tokenRefreshers,
        List<FeedbackChannel> feedbackChannels,
        List<InlineFindingChannel> inlineFindingChannels,
        List<ApprovalChannel> approvalChannels,
        List<IntegrationLifecycleListener> lifecycleListeners
    ) {
        this.manifests = manifests;
        this.signatureVerifiers = signatureVerifiers;
        this.secretSources = secretSources;
        this.subjectKeyDerivers = subjectKeyDerivers;
        this.subjectParsers = subjectParsers;
        this.credentialProviders = credentialProviders;
        this.tokenRefreshers = tokenRefreshers;
        this.feedbackChannels = feedbackChannels;
        this.inlineFindingChannels = inlineFindingChannels;
        this.approvalChannels = approvalChannels;
        this.lifecycleListeners = lifecycleListeners;
    }

    @PostConstruct
    public void validate() {
        List<String> violations = new ArrayList<>();
        for (IntegrationKind kind : manifests.registeredKinds()) {
            IntegrationManifest manifest = manifests.manifestFor(kind).orElseThrow();
            Set<Capability> declared = manifest.declaredCapabilities();
            checkRequired(kind, declared, violations);
        }
        if (!violations.isEmpty()) {
            String joined = String.join("\n  - ", violations);
            throw new IllegalStateException(
                "IntegrationManifest validation failed:\n  - " + joined
            );
        }
        log.info("Integration framework bootstrap OK ({} kinds registered)", manifests.registeredKinds().size());
    }

    private void checkRequired(IntegrationKind kind, Set<Capability> declared, List<String> violations) {
        // Universal: every registered kind needs a credential provider.
        require(kind, "ApiCredentialProvider", anyMatchKind(credentialProviders, p -> p.kind() == kind), violations);

        // Universal: every registered kind needs a lifecycle listener so Connection state
        // transitions can be wired into the right vendor adapter (even when the body is
        // a no-op stub — that's per-kind policy, not a framework gap).
        require(kind, "IntegrationLifecycleListener",
            anyMatchKind(lifecycleListeners, l -> l.kind() == kind), violations);

        if (declared.contains(Capability.WEBHOOK_INGEST)) {
            require(kind, "WebhookSignatureVerifier", anyMatchKind(signatureVerifiers, v -> v.kind() == kind), violations);
            require(kind, "WebhookSecretSource", anyMatchKind(secretSources, s -> s.kind() == kind), violations);
            require(kind, "SubjectKeyDeriver", anyMatchKind(subjectKeyDerivers, s -> s.kind() == kind), violations);
            require(kind, "SubjectParser", anyMatchKind(subjectParsers, s -> s.kind() == kind), violations);
        }
        if (declared.contains(Capability.URL_VERIFICATION_HANDSHAKE)) {
            // Currently piggy-backs on WebhookSignatureVerifier (Slack's verifier short-
            // circuits on url_verification). Enforce both so the manifest can't drift.
            require(kind, "WebhookSignatureVerifier (url_verification handshake)",
                anyMatchKind(signatureVerifiers, v -> v.kind() == kind), violations);
        }
        if (declared.contains(Capability.REPLAY_PROTECTION)) {
            require(kind, "WebhookSignatureVerifier (replay-window check)",
                anyMatchKind(signatureVerifiers, v -> v.kind() == kind), violations);
        }
        if (declared.contains(Capability.TOKEN_REFRESH)) {
            require(kind, "TokenRefresher", anyMatchKind(tokenRefreshers, t -> t.kind() == kind), violations);
        }
        if (declared.contains(Capability.FEEDBACK_DELIVERY)) {
            require(kind, "FeedbackChannel", anyMatchKind(feedbackChannels, f -> f.kind() == kind), violations);
        }
        if (declared.contains(Capability.INLINE_FINDINGS)) {
            require(kind, "InlineFindingChannel", anyMatchKind(inlineFindingChannels, f -> f.kind() == kind), violations);
        }
        if (declared.contains(Capability.APPROVAL_WORKFLOW)) {
            require(kind, "ApprovalChannel", anyMatchKind(approvalChannels, f -> f.kind() == kind), violations);
        }
        if (declared.contains(Capability.SCOPE_CHANGES)) {
            // SCOPE_CHANGES says "this vendor will fire onScopeChanged" — the listener
            // must therefore have a real body. We can't introspect for that, but we
            // can at least force the listener bean to be wired.
            require(kind, "IntegrationLifecycleListener (scope-change emitter)",
                anyMatchKind(lifecycleListeners, l -> l.kind() == kind), violations);
        }

        // Forward-compat: the moment a new Capability is added to the enum without a
        // matching check above, fail loud at boot instead of silently treating it as
        // satisfied.
        Set<Capability> unmapped = EnumSet.copyOf(declared);
        unmapped.removeAll(ENFORCED_CAPABILITIES);
        for (Capability cap : unmapped) {
            violations.add(kind + " declares capability " + cap
                + " but the bootstrap has no enforcement rule for it — add a require() branch");
        }
    }

    /** Capabilities the {@link #checkRequired} switch above pins to a bean check. */
    private static final Set<Capability> ENFORCED_CAPABILITIES = EnumSet.of(
        Capability.WEBHOOK_INGEST,
        Capability.URL_VERIFICATION_HANDSHAKE,
        Capability.REPLAY_PROTECTION,
        Capability.TOKEN_REFRESH,
        Capability.FEEDBACK_DELIVERY,
        Capability.INLINE_FINDINGS,
        Capability.APPROVAL_WORKFLOW,
        Capability.SCOPE_CHANGES
    );

    private static <T> boolean anyMatchKind(List<T> beans, Predicate<T> predicate) {
        return beans.stream().anyMatch(predicate);
    }

    private static void require(IntegrationKind kind, String spi, boolean present, List<String> violations) {
        if (!present) {
            violations.add(kind + " declares capability but no " + spi + " bean is wired");
        }
    }

    /** Returns the union of capabilities for the given set of active kinds. */
    public Set<Capability> capabilitiesFor(Set<IntegrationKind> activeKinds) {
        Set<Capability> all = new HashSet<>();
        for (IntegrationKind kind : activeKinds) {
            all.addAll(manifests.capabilitiesFor(kind));
        }
        return Set.copyOf(all);
    }
}

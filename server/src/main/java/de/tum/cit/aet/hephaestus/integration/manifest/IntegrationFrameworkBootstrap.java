package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.spi.ApprovalChannel;
import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.spi.RateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectParser;
import de.tum.cit.aet.hephaestus.integration.spi.SyncSource;
import de.tum.cit.aet.hephaestus.integration.spi.TokenRefresher;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup-time validation that every declared {@link Capability} has its required SPI
 * beans wired for the same kind.
 *
 * <p>Gated to the application-server runtime role — webhook and worker pods do not have
 * every SPI bean wired, so manifest validation only runs where the full set is expected.
 *
 * <p>On failure: throws on startup so misconfigurations surface immediately rather than
 * at the first request.
 */
@Component
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class IntegrationFrameworkBootstrap { /* matchIfMissing=true: validation runs by default on the app-server runtime role */

    private static final Logger log = LoggerFactory.getLogger(IntegrationFrameworkBootstrap.class);

    private final IntegrationManifestRegistry manifests;
    private final List<WebhookSignatureVerifier> signatureVerifiers;
    private final List<WebhookSecretSource> secretSources;
    private final List<SubjectKeyDeriver> subjectKeyDerivers;
    private final List<SubjectParser> subjectParsers;
    private final List<ApiCredentialProvider> credentialProviders;
    private final List<TokenRefresher> tokenRefreshers;
    private final List<RateLimitTracker> rateLimitTrackers;
    private final List<SyncSource> syncSources;
    private final List<FeedbackChannel> feedbackChannels;
    private final List<InlineFindingChannel> inlineFindingChannels;
    private final List<ApprovalChannel> approvalChannels;

    public IntegrationFrameworkBootstrap(
        IntegrationManifestRegistry manifests,
        List<WebhookSignatureVerifier> signatureVerifiers,
        List<WebhookSecretSource> secretSources,
        List<SubjectKeyDeriver> subjectKeyDerivers,
        List<SubjectParser> subjectParsers,
        List<ApiCredentialProvider> credentialProviders,
        List<TokenRefresher> tokenRefreshers,
        List<RateLimitTracker> rateLimitTrackers,
        List<SyncSource> syncSources,
        List<FeedbackChannel> feedbackChannels,
        List<InlineFindingChannel> inlineFindingChannels,
        List<ApprovalChannel> approvalChannels
    ) {
        this.manifests = manifests;
        this.signatureVerifiers = signatureVerifiers;
        this.secretSources = secretSources;
        this.subjectKeyDerivers = subjectKeyDerivers;
        this.subjectParsers = subjectParsers;
        this.credentialProviders = credentialProviders;
        this.tokenRefreshers = tokenRefreshers;
        this.rateLimitTrackers = rateLimitTrackers;
        this.syncSources = syncSources;
        this.feedbackChannels = feedbackChannels;
        this.inlineFindingChannels = inlineFindingChannels;
        this.approvalChannels = approvalChannels;
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
        // Universal: every registered kind needs a credential provider + manifest.
        require(kind, "ApiCredentialProvider", anyMatchKind(credentialProviders, p -> p.kind() == kind), violations);

        if (declared.contains(Capability.WEBHOOK_INGEST)) {
            require(kind, "WebhookSignatureVerifier", anyMatchKind(signatureVerifiers, v -> v.kind() == kind), violations);
            require(kind, "WebhookSecretSource", anyMatchKind(secretSources, s -> s.kind() == kind), violations);
            require(kind, "SubjectKeyDeriver", anyMatchKind(subjectKeyDerivers, s -> s.kind() == kind), violations);
            require(kind, "SubjectParser", anyMatchKind(subjectParsers, s -> s.kind() == kind), violations);
        }
        if (declared.contains(Capability.TOKEN_REFRESH)) {
            require(kind, "TokenRefresher", anyMatchKind(tokenRefreshers, t -> t.kind() == kind), violations);
        }
        if (declared.contains(Capability.RATE_LIMITED)) {
            require(kind, "RateLimitTracker", !rateLimitTrackers.isEmpty(), violations);
        }
        if (declared.contains(Capability.BACKFILL_SYNC)) {
            require(kind, "SyncSource", anyMatchKind(syncSources, s -> s.kind() == kind), violations);
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
    }

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

package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads signal coverage straight off the manifests — no probing, no runtime negotiation.
 *
 * <p>We compile every integration in one build, so what an integration can raise is a static fact that
 * a manifest already states. The only thing that varies per workspace is which integrations are
 * connected, and that is a single lookup rather than a capability handshake.
 */
@Component
public class DeclaredSignalCoverage implements SignalCoverage {

    private final IntegrationManifestRegistry manifests;
    private final ConnectionService connections;

    public DeclaredSignalCoverage(IntegrationManifestRegistry manifests, ConnectionService connections) {
        this.manifests = manifests;
        this.connections = connections;
    }

    @Override
    public Set<SignalName> compiledCoverage() {
        Set<SignalName> covered = new HashSet<>();
        for (IntegrationKind kind : manifests.registeredKinds()) {
            covered.addAll(contributionOf(kind).allRaisedSignals());
        }
        return Set.copyOf(covered);
    }

    @Override
    public Set<SignalName> connectedCoverage(long workspaceId) {
        Set<SignalName> covered = new HashSet<>();
        for (IntegrationKind kind : manifests.registeredKinds()) {
            if (connections.findActive(workspaceId, kind).isPresent()) {
                covered.addAll(contributionOf(kind).allRaisedSignals());
            }
        }
        return Set.copyOf(covered);
    }

    @Override
    public Set<IntegrationKind> raisedBy(SignalName signal) {
        return manifests
            .registeredKinds()
            .stream()
            .filter(kind -> contributionOf(kind).allRaisedSignals().contains(signal))
            .collect(Collectors.toUnmodifiableSet());
    }

    private IntegrationManifest.ReviewContribution contributionOf(IntegrationKind kind) {
        return manifests
            .manifestFor(kind)
            .map(IntegrationManifest::reviewContribution)
            .orElseGet(IntegrationManifest.ReviewContribution::none);
    }
}

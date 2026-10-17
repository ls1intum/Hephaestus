package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventCoverage;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DeclaredItemEventCoverage implements ItemEventCoverage {

    private final IntegrationManifestRegistry manifests;
    private final ConnectionService connections;

    public DeclaredItemEventCoverage(IntegrationManifestRegistry manifests, ConnectionService connections) {
        this.manifests = manifests;
        this.connections = connections;
    }

    @Override
    public Set<ItemEventName> compiledCoverage() {
        Set<ItemEventName> covered = new HashSet<>();
        for (IntegrationKind kind : manifests.registeredKinds()) {
            covered.addAll(contributionOf(kind).allRaisedEvents());
        }
        return Set.copyOf(covered);
    }

    @Override
    public Set<ItemEventName> connectedCoverage(long workspaceId) {
        Set<ItemEventName> covered = new HashSet<>();
        for (IntegrationKind kind : manifests.registeredKinds()) {
            if (connections.findActive(workspaceId, kind).isPresent()) {
                covered.addAll(contributionOf(kind).allRaisedEvents());
            }
        }
        return Set.copyOf(covered);
    }

    @Override
    public Set<IntegrationKind> raisedBy(ItemEventName event) {
        return manifests.registeredKinds().stream()
                .filter(kind -> contributionOf(kind).allRaisedEvents().contains(event))
                .collect(Collectors.toUnmodifiableSet());
    }

    private IntegrationManifest.ReviewContribution contributionOf(IntegrationKind kind) {
        return manifests
                .manifestFor(kind)
                .map(IntegrationManifest::reviewContribution)
                .orElseGet(IntegrationManifest.ReviewContribution::none);
    }
}

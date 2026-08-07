package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Per-kind {@link ArtifactDescriptor} registry, built from constructor-injected beans.
 *
 * <p>Two descriptors for one kind is fatal rather than last-one-wins: the descriptor decides which
 * signals exist and which lanes a kind has, so shadowing one with another would change what practices
 * can be bound to depending on bean ordering. Same reasoning as {@code IntegrationManifestRegistry},
 * and the message names both offenders for the same reason.
 */
@Component
public class ArtifactDescriptorRegistry implements ArtifactCatalog {

    private final Map<ArtifactKind, ArtifactDescriptor> byKind;

    public ArtifactDescriptorRegistry(List<ArtifactDescriptor> descriptors) {
        this.byKind = descriptors
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ArtifactDescriptor::kind,
                    d -> d,
                    (a, b) -> {
                        throw new IllegalStateException(
                            "Duplicate ArtifactDescriptor for kind=" +
                                a.kind() +
                                ": " +
                                a.getClass().getName() +
                                " vs " +
                                b.getClass().getName()
                        );
                    }
                )
            );
    }

    @Override
    public Collection<ArtifactDescriptor> all() {
        return byKind.values();
    }

    @Override
    public Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }
}

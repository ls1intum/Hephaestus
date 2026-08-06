package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
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
public class ArtifactDescriptorRegistry {

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

    public Set<ArtifactKind> registeredKinds() {
        return byKind.keySet();
    }

    public Collection<ArtifactDescriptor> all() {
        return byKind.values();
    }

    public Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }

    /**
     * The declared signal with this name, resolved through the kind its name already carries. Returns
     * empty when the kind has no descriptor or the descriptor does not declare the name — the caller
     * distinguishes those two by asking {@link #descriptorFor(ArtifactKind)} as well.
     */
    public Optional<Signal> signal(SignalName name) {
        return descriptorFor(name.artifactKind()).flatMap(descriptor -> descriptor.signal(name));
    }
}

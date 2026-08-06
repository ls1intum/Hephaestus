package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-kind capability declaration validated at application-server startup.
 *
 * <p>Each {@link Capability} declared MUST have a matching bean of the corresponding
 * SPI registered for the same {@link IntegrationKind}. {@code IntegrationFrameworkBootstrap}
 * iterates all manifests on startup and fail-fasts if any declared capability lacks
 * its wiring.
 *
 * <p>Capabilities describe plumbing — whether webhooks arrive, whether tokens refresh. They say nothing
 * about what the integration contributes to a practice review, which is why {@link #reviewContribution()}
 * exists as a second, separate section of the same declaration. It is deliberately not a free-standing
 * registration mechanism: an integration has one manifest, and everything true of it is stated there.
 *
 * <p>Manifests live in vendor packages ({@code integration/<kind>/manifest/...}).
 */
public interface IntegrationManifest {
    IntegrationKind kind();

    String displayName();

    Set<Capability> declaredCapabilities();

    /**
     * What this integration contributes to practice review. Intentionally has no default: an integration
     * that contributes nothing must say {@link ReviewContribution#none()} out loud, because the
     * alternative — inheriting silence — is exactly how an integration ends up ingesting events that can
     * never trigger anything without anyone noticing.
     */
    ReviewContribution reviewContribution();

    /**
     * The practice-review section of a manifest.
     *
     * <p>{@link #raises()} is the interesting field and it is deliberately a <em>subset</em> of what the
     * artifact's {@code ArtifactDescriptor} declares. The descriptor states what the domain can express;
     * this states what this vendor actually delivers of it. Today GitLab's webhook path emits no
     * synchronize event at all, so it cannot raise {@code scm.pull_request.synchronized} — and with this
     * record that becomes a fact the system can read, report as a dormant binding, and eventually close,
     * instead of a practice that quietly never fires for half the workspaces.
     *
     * @param observes the artifact kinds this integration writes to at all
     * @param raises   per kind, the signals this integration can actually raise; every entry must be
     *                 declared by that kind's descriptor and must be backed by an ingested event of this
     *                 integration
     * @param delivers per kind, the feedback lanes this integration will put feedback in; each lane is
     *                 held to the {@link Capability} that carries it
     */
    record ReviewContribution(
        Set<ArtifactKind> observes,
        Map<ArtifactKind, Set<SignalName>> raises,
        Map<ArtifactKind, Set<FeedbackLane>> delivers
    ) {
        private static final ReviewContribution NONE = new ReviewContribution(Set.of(), Map.of(), Map.of());

        public ReviewContribution {
            observes = Set.copyOf(Objects.requireNonNullElse(observes, Set.of()));
            raises = deepCopy(Objects.requireNonNullElse(raises, Map.of()));
            delivers = deepCopy(Objects.requireNonNullElse(delivers, Map.of()));
        }

        /** An integration that takes no part in practice review — a content source, or not wired yet. */
        public static ReviewContribution none() {
            return NONE;
        }

        public Set<SignalName> raisedSignals(ArtifactKind kind) {
            return raises.getOrDefault(kind, Set.of());
        }

        /** Every signal this integration raises, across all kinds. */
        public Set<SignalName> allRaisedSignals() {
            return raises.values().stream().flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
        }

        public Set<FeedbackLane> deliveredLanes(ArtifactKind kind) {
            return delivers.getOrDefault(kind, Set.of());
        }

        private static <V> Map<ArtifactKind, Set<V>> deepCopy(Map<ArtifactKind, Set<V>> source) {
            return source
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        }
    }
}

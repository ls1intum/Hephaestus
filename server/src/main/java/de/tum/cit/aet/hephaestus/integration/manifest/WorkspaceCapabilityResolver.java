package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Resolves which {@link Capability capabilities} are currently exposed by a workspace
 * (the union over its ACTIVE {@link Connection}s' per-kind
 * {@link de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest manifests}) and
 * answers whether a given (capabilities, family) requirement is satisfied there.
 *
 * <p>This is the single read-side authority for UI gating and orchestrator eligibility
 * checks: code SHOULD NOT pluck capabilities directly off the manifest registry — that
 * ignores Connection state and family narrowing.
 *
 * <p><b>No upstream entity types are referenced from here.</b> Callers pass primitive
 * requirements (a {@code Set<Capability>} and an optional {@link
 * IntegrationFamily.Family}) so we keep a clean module boundary — the integration
 * module never reaches into practices, mentor, or any other downstream model. A thin
 * caller-side adapter (e.g. {@code PracticeAvailabilityResolver} in the practices
 * module) projects domain entities to the primitive shape expected here.
 *
 * <p>Stateless and side-effect free; safe to inject anywhere.
 */
@Service
public class WorkspaceCapabilityResolver {

    private final ConnectionRepository connectionRepository;
    private final IntegrationManifestRegistry manifestRegistry;

    public WorkspaceCapabilityResolver(
        ConnectionRepository connectionRepository,
        IntegrationManifestRegistry manifestRegistry
    ) {
        this.connectionRepository = connectionRepository;
        this.manifestRegistry = manifestRegistry;
    }

    /**
     * Returns the immutable union of capabilities declared by manifests of every
     * ACTIVE Connection owned by the workspace. Connections in PENDING / SUSPENDED /
     * UNINSTALLED states do not contribute — they cannot serve traffic, so the UI
     * must not advertise their capabilities as available.
     *
     * <p>Unknown kinds (Connection rows whose kind has no registered manifest, e.g.
     * during a partial deploy) contribute the empty set rather than crashing.
     */
    public Set<Capability> activeCapabilitiesFor(long workspaceId) {
        List<Connection> active = connectionRepository.findByWorkspaceIdAndState(
            workspaceId, IntegrationState.ACTIVE
        );
        if (active.isEmpty()) {
            return Set.of();
        }
        Set<Capability> union = new HashSet<>();
        for (Connection connection : active) {
            union.addAll(manifestRegistry.capabilitiesFor(connection.getKind()));
        }
        return Set.copyOf(union);
    }

    /**
     * Returns the set of ACTIVE kinds for the workspace. Pure helper — equivalent to
     * looking up Connections and mapping to {@link Connection#getKind()}, but exposed
     * so callers don't have to take a {@link ConnectionRepository} dependency just to
     * answer "what's plugged in here?".
     */
    public Set<IntegrationKind> activeKindsFor(long workspaceId) {
        List<Connection> active = connectionRepository.findByWorkspaceIdAndState(
            workspaceId, IntegrationState.ACTIVE
        );
        if (active.isEmpty()) {
            return Set.of();
        }
        Set<IntegrationKind> kinds = new HashSet<>();
        for (Connection connection : active) {
            kinds.add(connection.getKind());
        }
        return Set.copyOf(kinds);
    }

    /**
     * True iff every capability in {@code requiredCapabilities} is exposed by the
     * workspace AND, when {@code requiredFamily} is non-null, at least one ACTIVE
     * Connection belongs to that family.
     *
     * <p>The check is short-circuited when both arguments are empty/null — a practice
     * (or other consumer) with no constraints is universally available and no DB hit
     * is paid.
     *
     * <p>Callers are expected to have already filtered out unknown capability strings
     * — typically by going through {@code Practice#getRequiredCapabilitySet()} or an
     * equivalent forward-compat helper — so this method treats {@code
     * requiredCapabilities} as the authoritative effective requirement.
     */
    public boolean isAvailable(
        long workspaceId,
        Set<Capability> requiredCapabilities,
        @Nullable IntegrationFamily.Family requiredFamily
    ) {
        Set<Capability> required = requiredCapabilities == null ? Set.of() : requiredCapabilities;

        if (required.isEmpty() && requiredFamily == null) {
            return true;
        }

        List<Connection> active = connectionRepository.findByWorkspaceIdAndState(
            workspaceId, IntegrationState.ACTIVE
        );
        if (active.isEmpty()) {
            return false;
        }

        if (requiredFamily != null) {
            boolean familyPresent = active.stream()
                .anyMatch(c -> c.getKind().family() == requiredFamily);
            if (!familyPresent) {
                return false;
            }
        }

        if (required.isEmpty()) {
            return true;
        }
        Set<Capability> available = new HashSet<>();
        for (Connection connection : active) {
            available.addAll(manifestRegistry.capabilitiesFor(connection.getKind()));
            if (available.containsAll(required)) {
                return true;
            }
        }
        return available.containsAll(required);
    }
}

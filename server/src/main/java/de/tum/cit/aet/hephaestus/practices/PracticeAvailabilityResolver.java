package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.framework.WorkspaceCapabilityResolver;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import org.springframework.stereotype.Service;

/**
 * Practices-module shim over
 * {@link WorkspaceCapabilityResolver#isAvailable(long,
 * java.util.Set, de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily)}.
 *
 * <p>Lives here (and not in the integration module) so the integration module never
 * imports {@link Practice} — keeping the dependency arrow one-way, {@code practices ->
 * integration}, and Spring Modulith's slice-cycle check green.
 *
 * <p>Projects a {@link Practice} to the primitive (capabilities, family) shape the
 * resolver expects. Unknown capability strings in the catalog are dropped by
 * {@link Practice#getRequiredCapabilitySet()} before the call — see that method's
 * Javadoc for the forward-compat rationale.
 */
@Service
public class PracticeAvailabilityResolver {

    private final WorkspaceCapabilityResolver capabilityResolver;

    public PracticeAvailabilityResolver(WorkspaceCapabilityResolver capabilityResolver) {
        this.capabilityResolver = capabilityResolver;
    }

    /**
     * Returns true iff the workspace exposes every capability the practice needs and
     * (when set) has at least one ACTIVE Connection in the required family. The
     * underlying resolver short-circuits when both constraints are empty, so a
     * universally-applicable practice never hits the database.
     */
    public boolean isAvailable(long workspaceId, Practice practice) {
        return capabilityResolver.isAvailable(
            workspaceId,
            practice.getRequiredCapabilitySet(),
            practice.getRequiredFamily()
        );
    }
}

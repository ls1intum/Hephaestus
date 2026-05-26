package de.tum.cit.aet.hephaestus.integration.registry;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.NoSuchElementException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link IntegrationKind} for a piece of work given its current
 * vendor binding plus a workspace fallback.
 *
 * <p>The agent-side rows now always carry the kind directly. The legacy fallback
 * that derived the kind from {@code workspace.git_provider_mode} is gone — Stage 1
 * of the #1198 cutover drops that column and the Connection registry is the
 * authoritative source. The fallback that used to consult {@code ConnectionService}
 * for un-stamped rows is omitted on purpose: when {@code directKind} is null the
 * agent row is malformed (created by code that pre-dates the registry) and should
 * surface as an error, not silently resolve.
 *
 * <p>Kept as a thin {@code @Component} so the existing call sites (which pass
 * {@code directKind} from the work row) don't need to change.
 */
@Component
public class JobIntegrationKindResolver {

    public JobIntegrationKindResolver() {
        // No collaborators — pure delegation now.
    }

    /**
     * @param directKind the kind recorded on the work row. Must be non-null after
     *     the #1198 cutover; agent jobs created without this column are not
     *     expected to exist in production.
     * @param workspaceId workspace id, kept in the signature for caller-site clarity
     *     (logs, future audit trails). Currently unused.
     * @return the {@link IntegrationKind} for this work item.
     * @throws NoSuchElementException if {@code directKind} is null
     */
    public IntegrationKind resolve(@Nullable IntegrationKind directKind, long workspaceId) {
        if (directKind != null) {
            return directKind;
        }
        throw new NoSuchElementException(
            "Cannot resolve IntegrationKind: directKind is null for workspaceId=" + workspaceId
                + ". Post-#1198 every agent/work row must carry its integration kind explicitly."
        );
    }
}

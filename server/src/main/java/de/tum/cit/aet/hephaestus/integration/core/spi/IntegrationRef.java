package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Stable handle to a specific Connection.
 *
 * <p>The {@code instanceKey} disambiguates multi-instance scenarios: a workspace with
 * two GitHub orgs has two Connection rows, each with a distinct instance_key
 * (GitHub installation_id). Set to {@code null} only for unbound bootstrap state.
 *
 * <p>Per-kind derivation:
 * <ul>
 *   <li>GitHub: {@code instanceKey} = installation id as string (or {@code "pat"} for PAT_ORG)
 *   <li>GitLab: {@code instanceKey} = {@code "<host>:<group_id>"}
 *   <li>Slack: {@code instanceKey} = team id
 * </ul>
 */
public record IntegrationRef(@NonNull IntegrationKind kind, long workspaceId, @Nullable String instanceKey) {
    public IntegrationRef {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
    }

    public IntegrationFamily family() {
        return kind.family();
    }
}

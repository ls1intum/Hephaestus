package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Identifies a connection row; {@code connectionId} also resolves non-active rows during teardown. */
public record IntegrationRef(
    @NonNull IntegrationKind kind,
    long workspaceId,
    @Nullable String instanceKey,
    @Nullable Long connectionId
) {
    public IntegrationRef(IntegrationKind kind, long workspaceId, @Nullable String instanceKey) {
        this(kind, workspaceId, instanceKey, null);
    }

    public IntegrationRef {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
    }

    public IntegrationFamily family() {
        return kind.family();
    }
}

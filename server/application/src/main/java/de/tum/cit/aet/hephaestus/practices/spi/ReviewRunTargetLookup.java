package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface ReviewRunTargetLookup {
    Map<UUID, Target> findByJobIds(long workspaceId, Collection<UUID> jobIds);

    record Target(
            @NonNull ArtifactKind type,
            @Nullable Long id,
            @Nullable IntegrationKind provider,
            @Nullable Integer number,
            @NonNull String title,
            @Nullable String repositoryName,
            @Nullable String channelName,
            @Nullable String url) {}
}

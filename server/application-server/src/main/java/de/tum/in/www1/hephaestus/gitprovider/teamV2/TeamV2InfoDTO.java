package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamV2InfoDTO(
    @NonNull Long id,
    @NonNull String nodeId,
    @NonNull String slug,
    @NonNull String name,
    String description,
    String privacy,
    String organization,
    String apiUrl,
    String htmlUrl,
    OffsetDateTime lastSyncedAt,
    int membershipCount,
    int repoPermissionCount
) {}

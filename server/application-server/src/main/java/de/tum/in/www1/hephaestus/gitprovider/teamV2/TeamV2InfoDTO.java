package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2.Privacy;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamV2InfoDTO(
    @NonNull Long id,
    @NonNull String name,
    Long parentId,
    String description,
    Privacy privacy,
    String organization,
    String htmlUrl,
    OffsetDateTime lastSyncedAt,
    int membershipCount,
    int repoPermissionCount
) {}

package de.tum.in.www1.hephaestus.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetaDataDTO(
    @NonNull List<TeamInfoDTO> teams,
    @NonNull String scheduledDay,
    @NonNull String scheduledTime
) {}

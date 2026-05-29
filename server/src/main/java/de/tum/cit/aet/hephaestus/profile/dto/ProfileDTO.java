package de.tum.cit.aet.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User profile header: identity, league standing, contribution surface, XP")
public record ProfileDTO(
    @NonNull @Schema(description = "Basic information about the user") UserInfoDTO userInfo,
    @Schema(description = "Timestamp of the user's first contribution") Instant firstContribution,
    @NonNull
    @Schema(description = "Repositories the user has contributed to")
    List<RepositoryInfoDTO> contributedRepositories,
    @NonNull @Schema(description = "XP progress information for the users' profile") ProfileXpRecordDTO xpRecord
) {}

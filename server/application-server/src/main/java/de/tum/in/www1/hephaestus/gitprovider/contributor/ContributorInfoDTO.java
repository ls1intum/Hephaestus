package de.tum.in.www1.hephaestus.gitprovider.contributor;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ContributorInfoDTO(@NonNull Long id, @NonNull UserInfoDTO user, int contributions) {
    public static ContributorInfoDTO fromContributor(Contributor contributor) {
        return new ContributorInfoDTO(
            contributor.getId(),
            UserInfoDTO.fromUser(contributor.getUser()),
            contributor.getContributions()
        );
    }
}

package de.tum.in.www1.hephaestus.meta;

import java.io.IOException;
import org.kohsuke.github.GHRepository.Contributor;
import org.springframework.lang.NonNull;

public record ContributorDTO(
    @NonNull Long id,
    @NonNull String login,
    @NonNull String name,
    @NonNull String avatarUrl,
    @NonNull String htmlUrl,
    int contributions
) {
    public static ContributorDTO fromContributor(Contributor contributor) throws IOException {
        return new ContributorDTO(
            contributor.getId(),
            contributor.getLogin(),
            contributor.getName(),
            contributor.getAvatarUrl(),
            contributor.getHtmlUrl().toString(),
            contributor.getContributions()
        );
    }
}

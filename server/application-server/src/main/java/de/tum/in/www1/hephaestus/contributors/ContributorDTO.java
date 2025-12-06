package de.tum.in.www1.hephaestus.contributors;

import java.io.IOException;
import org.kohsuke.github.GHRepository.Contributor;
import org.springframework.lang.NonNull;

/**
 * Data transfer object representing a GitHub contributor.
 * Used to display contributor information on the public about page.
 */
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

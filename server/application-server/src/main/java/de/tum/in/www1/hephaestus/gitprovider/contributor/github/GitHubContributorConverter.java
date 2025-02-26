package de.tum.in.www1.hephaestus.gitprovider.contributor.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.contributor.Contributor;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubContributorConverter extends BaseGitServiceEntityConverter<GHRepository.Contributor, Contributor> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubContributorConverter.class);

    @Override
    public Contributor convert(@NonNull GHRepository.Contributor source) {
        return update(source, new Contributor());
    }

    public Contributor convert(
        @NonNull GHRepository.Contributor source,
        @NonNull Repository repository,
        @NonNull User user
    ) {
        return update(source, new Contributor(), repository, user);
    }

    public Contributor update(
        @NonNull GHRepository.Contributor source,
        @NonNull Contributor contributor,
        @NonNull Repository repository,
        @NonNull User user
    ) {
        convertBaseFields(source, contributor);
        contributor.setRepository(repository);
        contributor.setUser(user);
        contributor.setContributions(source.getContributions());
        return contributor;
    }

    /**
     * @implNote Warning:
     * An instance of the repository and user is required to properly update a contributor.
     * This method only updates the base fields of the contributor.
     */
    @Override
    public Contributor update(@NonNull GHRepository.Contributor source, @NonNull Contributor contributor) {
        logger.warn("Called update without repository and user. This method should not be used directly.");
        convertBaseFields(source, contributor);
        return contributor;
    }
}

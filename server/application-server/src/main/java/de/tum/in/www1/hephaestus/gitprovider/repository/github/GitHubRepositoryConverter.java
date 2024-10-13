package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.base.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;

@Component
public class GitHubRepositoryConverter extends BaseGitServiceEntityConverter<GHRepository, Repository> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryConverter.class);

    @Override
    public Repository convert(@NonNull GHRepository source) {
        return update(source, new Repository());
    }
    
    @Override
    public Repository update(@NonNull GHRepository source, @NonNull Repository repository) {
        convertBaseFields(source, repository);
        repository.setName(source.getName());
        repository.setNameWithOwner(source.getFullName());
        repository.setPrivate(source.isPrivate());
        repository.setHtmlUrl(source.getHtmlUrl().toString());
        repository.setDescription(source.getDescription());
        repository.setHomepage(source.getHomepage());
        repository.setPushedAt(convertToOffsetDateTime(source.getPushedAt()));
        repository.setArchived(source.isArchived());
        repository.setDisabled(source.isDisabled());
        repository.setVisibility(convertVisibility(source.getVisibility()));
        repository.setStargazersCount(source.getStargazersCount());
        repository.setWatchersCount(source.getWatchersCount());
        repository.setSubscribersCount(source.getSubscribersCount());
        repository.setDefaultBranch(source.getDefaultBranch());
        repository.setHasIssues(source.hasIssues());
        repository.setHasProjects(source.hasProjects());
        repository.setHasWiki(source.hasWiki());
        return repository;
    }

    private Repository.Visibility convertVisibility(Visibility visibility) {
        switch (visibility) {
            case PRIVATE:
                return Repository.Visibility.PRIVATE;
            case PUBLIC:
                return Repository.Visibility.PUBLIC;
            case INTERNAL:
                return Repository.Visibility.INTERNAL;
            default:
                logger.error("Unknown repository visibility: {}", visibility);
                return Repository.Visibility.UNKNOWN;
        }
    }
}

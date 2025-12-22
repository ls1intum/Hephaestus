package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.io.IOException;
import java.time.Instant;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use webhook DTOs and repository processing services instead.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
@Component
public class GitHubRepositoryConverter extends BaseGitServiceEntityConverter<GHRepository, Repository> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryConverter.class);

    @Autowired
    OrganizationService organizationService;

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
        if (source.getHtmlUrl() != null) {
            repository.setHtmlUrl(source.getHtmlUrl().toString());
        } else if (repository.getHtmlUrl() == null && repository.getNameWithOwner() != null) {
            repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        }
        repository.setDescription(sanitizeForPostgres(source.getDescription()));
        repository.setHomepage(source.getHomepage());
        Instant pushedAt = source.getPushedAt();
        if (pushedAt != null) {
            repository.setPushedAt(pushedAt);
        } else if (repository.getPushedAt() == null) {
            repository.setPushedAt(repository.getUpdatedAt() != null ? repository.getUpdatedAt() : Instant.now());
        }
        repository.setArchived(source.isArchived());
        repository.setDisabled(source.isDisabled());
        repository.setVisibility(convertVisibility(source.getVisibility()));
        repository.setStargazersCount(source.getStargazersCount());
        repository.setWatchersCount(source.getWatchersCount());
        var defaultBranch = source.getDefaultBranch();
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            repository.setDefaultBranch(defaultBranch);
        }
        repository.setHasIssues(source.hasIssues());
        repository.setHasProjects(source.hasProjects());
        repository.setHasWiki(source.hasWiki());

        try {
            var owner = source.getOwner();
            if (owner != null && "Organization".equalsIgnoreCase(owner.getType())) {
                long orgId = owner.getId();
                String orgLogin = owner.getLogin();
                Organization org = organizationService.upsertIdentity(orgId, orgLogin);

                repository.setOrganization(org);
            }
        } catch (IOException e) {
            logger.error("Couldn't fetch organization owner for repository: {}", source.getName());
        }

        return repository;
    }

    private Repository.Visibility convertVisibility(Visibility visibility) {
        if (visibility == null) {
            return Repository.Visibility.UNKNOWN;
        }
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

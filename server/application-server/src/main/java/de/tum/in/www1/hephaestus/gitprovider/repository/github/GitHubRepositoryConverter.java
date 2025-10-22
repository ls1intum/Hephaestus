package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.organization.Organization;
import de.tum.in.www1.hephaestus.organization.OrganizationService;
import java.io.IOException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

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
        repository.setHtmlUrl(source.getHtmlUrl().toString());
        repository.setDescription(source.getDescription());
        repository.setHomepage(source.getHomepage());
        repository.setPushedAt(source.getPushedAt());
        repository.setArchived(source.isArchived());
        repository.setDisabled(source.isDisabled());
        repository.setVisibility(convertVisibility(source.getVisibility()));
        repository.setStargazersCount(source.getStargazersCount());
        repository.setWatchersCount(source.getWatchersCount());
        repository.setDefaultBranch(source.getDefaultBranch());
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

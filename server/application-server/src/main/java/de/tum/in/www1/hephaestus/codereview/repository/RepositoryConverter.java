package de.tum.in.www1.hephaestus.codereview.repository;

import java.io.IOException;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class RepositoryConverter extends BaseGitServiceEntityConverter<GHRepository, Repository> {

    protected static final Logger logger = LoggerFactory.getLogger(RepositoryConverter.class);

    @Override
    public Repository convert(@NonNull GHRepository source) {
        Repository repository = new Repository();
        convertBaseFields(source, repository);
        repository.setName(source.getName());
        repository.setNameWithOwner(source.getFullName());
        repository.setDescription(source.getDescription());
        repository.setUrl(source.getHtmlUrl().toString());
        repository.setDefaultBranch(source.getDefaultBranch());
        repository.setVisibility(convertVisibility(source.getVisibility()));
        repository.setHomepage(source.getHomepage());
        return repository;
    }

    @Override
    public Repository update(@NonNull GHRepository source, @NonNull Repository repository) {
        try {
            repository.setUpdatedAt(convertToOffsetDateTime(source.getUpdatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        return repository;
    }

    private RepositoryVisibility convertVisibility(Visibility visibility) {
        switch (visibility) {
            case PRIVATE:
                return RepositoryVisibility.PRIVATE;
            case PUBLIC:
                return RepositoryVisibility.PUBLIC;
            default:
                return RepositoryVisibility.PRIVATE;
        }
    }
}

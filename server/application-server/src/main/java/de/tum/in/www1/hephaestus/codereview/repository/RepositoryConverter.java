package de.tum.in.www1.hephaestus.codereview.repository;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class RepositoryConverter extends BaseGitServiceEntityConverter<GHRepository, Repository> {

    @Override
    @Nullable
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

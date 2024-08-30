package de.tum.in.www1.hephaestus.codereview.repository;

import org.kohsuke.github.GHRepository;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Service
@ReadingConverter
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
        return repository;
    }

}

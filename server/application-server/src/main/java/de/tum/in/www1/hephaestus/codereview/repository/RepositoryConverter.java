package de.tum.in.www1.hephaestus.codereview.repository;

import org.kohsuke.github.GHRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@ReadingConverter
public class RepositoryConverter implements Converter<GHRepository, Repository> {

    @Override
    @Nullable
    public Repository convert(@NonNull GHRepository source) {
        Repository repository = new Repository();
        repository.setName(source.getName());
        repository.setNameWithOwner(source.getFullName());
        repository.setUrl(source.getHtmlUrl().toString());
        repository.setDescription(source.getDescription());
        return repository;
    }

}

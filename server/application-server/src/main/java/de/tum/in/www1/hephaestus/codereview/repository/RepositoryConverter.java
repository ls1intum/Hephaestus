package de.tum.in.www1.hephaestus.codereview.repository;

import java.io.IOException;

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
        Long id = source.getId();
        String name = source.getName();
        String nameWithOwner = source.getFullName();
        String description = source.getDescription();
        String url = source.getHtmlUrl().toString();

        Repository repository;
        try {
            String createdAt = source.getCreatedAt().toString();
            String updatedAt = source.getUpdatedAt().toString();
            repository = new Repository(id, name, nameWithOwner, description, url, createdAt, updatedAt);
        } catch (IOException e) {
            repository = new Repository(id, name, nameWithOwner, description, url, null, null);
        }
        return repository;
    }

}

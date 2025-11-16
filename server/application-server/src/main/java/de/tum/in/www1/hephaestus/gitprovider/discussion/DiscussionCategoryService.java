package de.tum.in.www1.hephaestus.gitprovider.discussion;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.Objects;
import org.kohsuke.github.GHRepositoryDiscussion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscussionCategoryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscussionCategoryService.class);

    private final DiscussionCategoryRepository categoryRepository;

    public DiscussionCategoryService(DiscussionCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public DiscussionCategory upsertCategory(GHRepositoryDiscussion.Category source, Repository repository) {
        if (source == null || repository == null) {
            return null;
        }

        var existingCategory = categoryRepository.findById(source.getId());
        boolean isNew = existingCategory.isEmpty();
        var category = existingCategory.orElseGet(() -> instantiate(source, repository));
        boolean dirty = isNew;

        if (!Objects.equals(category.getRepository(), repository)) {
            category.setRepository(repository);
            dirty = true;
        }
        dirty |= setIfChanged(category::setName, category.getName(), source.getName());
        dirty |= setIfChanged(category::setSlug, category.getSlug(), source.getSlug());
        dirty |= setIfChanged(category::setEmoji, category.getEmoji(), source.getEmoji());
        dirty |= setIfChanged(category::setDescription, category.getDescription(), source.getDescription());
        if (category.isAnswerable() != source.isAnswerable()) {
            category.setAnswerable(source.isAnswerable());
            dirty = true;
        }

        if (dirty) {
            logger.debug(
                "Persisted discussion category {} ({}) for repository {}",
                source.getId(),
                source.getSlug(),
                repository.getNameWithOwner()
            );
            return categoryRepository.save(category);
        }
        return category;
    }

    private DiscussionCategory instantiate(GHRepositoryDiscussion.Category source, Repository repository) {
        var category = new DiscussionCategory();
        category.setId(source.getId());
        category.setRepository(repository);
        category.setName(source.getName());
        category.setSlug(source.getSlug());
        category.setEmoji(source.getEmoji());
        category.setDescription(source.getDescription());
        category.setAnswerable(source.isAnswerable());
        return category;
    }

    private boolean setIfChanged(java.util.function.Consumer<String> setter, String current, String candidate) {
        if (Objects.equals(current, candidate)) {
            return false;
        }
        setter.accept(candidate);
        return true;
    }
}

package de.tum.cit.aet.hephaestus.integration.scm.events;

import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Async domain events must contain immutable references rather than lazy JPA entities. */
public record RepositoryRef(
        @NonNull Long id,
        @NonNull String nameWithOwner,
        @NonNull String defaultBranch) {
    private static final Logger log = LoggerFactory.getLogger(RepositoryRef.class);

    /**
     * Creates a RepositoryRef from a JPA entity. Call this while the entity is still attached to
     * the persistence context.
     *
     * @param repository the repository entity, may be null
     * @return a RepositoryRef or null if repository is null
     */
    @Nullable
    public static RepositoryRef from(@Nullable Repository repository) {
        if (repository == null) {
            log.debug("Cannot create RepositoryRef: repository is null");
            return null;
        }
        return new RepositoryRef(
                repository.getId(),
                repository.getNameWithOwner(),
                repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main");
    }
}

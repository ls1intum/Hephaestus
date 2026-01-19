package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable reference to a repository for use in domain events.
 *
 * <p>Domain events must NOT contain JPA entities because:
 * <ul>
 *   <li>Async event handlers run outside the original transaction/session</li>
 *   <li>Lazy-loaded relationships will throw LazyInitializationException</li>
 *   <li>Entity state may change between event publication and handling</li>
 * </ul>
 *
 * <p>This value object captures the essential repository information needed
 * by event consumers without carrying the JPA entity.
 */
public record RepositoryRef(@NonNull Long id, @NonNull String nameWithOwner, @NonNull String defaultBranch) {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRef.class);

    /**
     * Creates a RepositoryRef from a JPA entity.
     * Call this while the entity is still attached to the persistence context.
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
            repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main"
        );
    }
}

package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
public class CatalogLock {
    @PersistenceContext
    private @Nullable EntityManager entityManager;

    public void acquire() {
        Objects.requireNonNull(entityManager)
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('hephaestus'), hashtext('practice-catalog'))")
                .getSingleResult();
    }
}

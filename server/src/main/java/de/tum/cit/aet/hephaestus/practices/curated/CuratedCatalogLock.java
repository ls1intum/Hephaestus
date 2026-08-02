package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
@WorkspaceAgnostic("Serializes instance catalog snapshots and writes")
public class CuratedCatalogLock {

    @PersistenceContext
    private EntityManager entityManager;

    public void acquire() {
        entityManager
            .createNativeQuery(
                "SELECT pg_advisory_xact_lock(hashtext('hephaestus'), hashtext('curated-practice-catalog'))"
            )
            .getSingleResult();
    }
}

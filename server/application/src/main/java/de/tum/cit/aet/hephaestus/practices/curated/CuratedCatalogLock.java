package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@WorkspaceAgnostic("Serializes instance catalog snapshots and writes")
@RequiredArgsConstructor
public class CuratedCatalogLock {

    private final EntityManager entityManager;

    public void acquire() {
        entityManager
            .createNativeQuery(
                "SELECT pg_advisory_xact_lock(hashtext('hephaestus'), hashtext('curated-practice-catalog'))"
            )
            .getSingleResult();
    }
}

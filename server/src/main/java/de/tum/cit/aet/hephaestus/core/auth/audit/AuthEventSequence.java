package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates {@code auth_event} ids from the table's own {@code auth_event_id_seq} (created
 * implicitly by the {@code BIGSERIAL id} column). The partitioned table's composite PK
 * {@code (id, occurred_at)} means Hibernate can't auto-generate the id, so we pull from the
 * sequence explicitly and assign client-side.
 */
@ConditionalOnServerRole
@Component
public class AuthEventSequence {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long next() {
        Object value = entityManager.createNativeQuery("SELECT nextval('auth_event_id_seq')").getSingleResult();
        return ((Number) value).longValue();
    }
}

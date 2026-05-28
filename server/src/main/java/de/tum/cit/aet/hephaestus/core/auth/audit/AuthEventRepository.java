package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Insert-mostly repository for {@link AuthEvent}. The table is append-only at the SQL-grant
 * level in non-test environments; this repository never exposes update / delete beyond what
 * {@link JpaRepository} provides (those methods are simply never called on the auth path).
 */
@Repository
@WorkspaceAgnostic("Auth audit events are account/system-scoped; workspace is an optional reference, not a tenant scope")
public interface AuthEventRepository extends JpaRepository<AuthEvent, AuthEvent.Id> {}

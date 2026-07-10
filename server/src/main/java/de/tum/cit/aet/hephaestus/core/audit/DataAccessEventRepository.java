package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.repository.Repository;

/**
 * Repository for {@link DataAccessEvent}.
 *
 * <p>Intentionally extends the bare {@link Repository} marker (not {@code JpaRepository}) so the append-only
 * contract is a compile-time guarantee: it exposes ONLY an insert ({@link #save}). There is no
 * {@code delete*} / {@code update*} surface — the sole sanctioned deletion is the marker-guarded native purge
 * in {@link DataAccessAuditWriter#purgeWorkspace}.
 */
@WorkspaceAgnostic("Audit rows carry a scalar workspace_id; every read filters on it explicitly")
public interface DataAccessEventRepository extends Repository<DataAccessEvent, Long> {
    /** Insert an audit row. */
    DataAccessEvent save(DataAccessEvent event);
}

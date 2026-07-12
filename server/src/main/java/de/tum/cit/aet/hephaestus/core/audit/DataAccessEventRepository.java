package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.repository.Repository;

/**
 * Repository for {@link DataAccessEvent}.
 *
 * <p>Intentionally extends the bare {@link Repository} marker (not {@code JpaRepository}) so the append-only
 * contract is a compile-time guarantee: it exposes ONLY an insert ({@link #save}). There is no
 * {@code delete*} / {@code update*} surface — the sole sanctioned deletion is the marker-guarded native purge
 * in {@link DataAccessAuditWriter#purgeWorkspace}, and the sole sanctioned update is the account-erasure
 * redaction in {@code core.auth.AccountPurger}. The application never reads this table; compliance and
 * subject-access requests are served manually via SQL.
 */
@WorkspaceAgnostic("Write-only audit rows carry a scalar workspace_id; compliance reads happen via manual SQL")
public interface DataAccessEventRepository extends Repository<DataAccessEvent, Long> {
    /** Insert an audit row. */
    DataAccessEvent save(DataAccessEvent event);
}

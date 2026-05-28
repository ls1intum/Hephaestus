package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Cleans up vendor-specific dependent entities when a repository is about to be deleted.
 *
 * <p>GitHub Projects v2 rows reference repositories via a polymorphic ownership column;
 * the foreign-key constraint blocks {@code DELETE repository} until those projects are
 * detached or removed. The workspace module calls this port before deleting an orphaned
 * repository so vendor-specific cleanup runs without the workspace having to import
 * vendor types.
 *
 * <p>No-op for kinds that have no analogous dependent entities. Implementations should
 * return the number of detached/deleted rows for telemetry.
 */
public interface ProjectIntegrityHook {
    /** Cascade-deletes vendor projects/equivalents owned by the given repository. */
    int cascadeDeleteForRepository(long repositoryId);
}

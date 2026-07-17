package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * The dedup key's shape and its failure handling. That an elevated request writes ONE attributed row
 * is covered end-to-end by {@code WorkspaceContextFilterIntegrationTest}; both properties here would
 * still write "a row" and pass that test.
 */
class WorkspaceElevationAuditAdapterTest extends BaseUnitTest {

    @Test
    void dedupIsPerAccountAndWorkspace() {
        AuthEventWriter authEventWriter = mock(AuthEventWriter.class);
        when(authEventWriter.write(any())).thenReturn(true);
        WorkspaceElevationAuditAdapter auditor = new WorkspaceElevationAuditAdapter(
            new AuthEventLogger(authEventWriter)
        );

        auditor.recordElevatedAccess(42L, 7L);
        auditor.recordElevatedAccess(42L, 7L); // same pair — deduped
        auditor.recordElevatedAccess(42L, 8L); // same admin, other workspace
        auditor.recordElevatedAccess(43L, 7L); // other admin, same workspace

        verify(authEventWriter, times(3)).write(any());
    }

    @Test
    void aFailedWriteDoesNotClaimTheDedupWindow() {
        // Otherwise one transient failure silently un-audits every elevated access to that workspace
        // for the whole window — the "never fewer rows than accesses" invariant this class claims.
        AuthEventWriter authEventWriter = mock(AuthEventWriter.class);
        when(authEventWriter.write(any())).thenReturn(false, true);
        WorkspaceElevationAuditAdapter auditor = new WorkspaceElevationAuditAdapter(
            new AuthEventLogger(authEventWriter)
        );

        auditor.recordElevatedAccess(42L, 7L); // write fails
        auditor.recordElevatedAccess(42L, 7L); // must retry, and succeed
        auditor.recordElevatedAccess(42L, 7L); // now deduped

        verify(authEventWriter, times(2)).write(any());
    }
}

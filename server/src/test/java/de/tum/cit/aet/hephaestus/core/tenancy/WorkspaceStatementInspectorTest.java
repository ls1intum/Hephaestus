package de.tum.cit.aet.hephaestus.core.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkspaceStatementInspectorTest extends BaseUnitTest {

    private final WorkspaceScopedTables scopedTables = mock(WorkspaceScopedTables.class);
    private final TenancyViolationReporter reporter = mock(TenancyViolationReporter.class);
    private final Counter parseFailureCounter = mock(Counter.class);

    @AfterEach
    void clearBypass() {
        while (TenancyBypass.isActive()) {
            // safety: ensure no test leaks ThreadLocal state into the next test
            // (BaseUnitTest does not fork)
            // depth-decrement happens via close; if we get here someone forgot
            // try-with-resources — fail loud below.
            throw new IllegalStateException("TenancyBypass not closed by prior test");
        }
    }

    private WorkspaceStatementInspector newInspector(TenancyEnforcement mode) {
        return new WorkspaceStatementInspector(scopedTables, mode, reporter, parseFailureCounter);
    }

    // ── short-circuits ─────────────────────────────────────────────────────

    @Test
    void offModeSkipsEverything() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.OFF);
        inspector.inspect("SELECT * FROM pull_request");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void bypassActiveSkipsEverything() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        try (TenancyBypass.Scope ignored = TenancyBypass.open("test")) {
            inspector.inspect("SELECT * FROM pull_request");
        }
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void blankSqlIsIgnored() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(null);
        inspector.inspect("");
        inspector.inspect("   ");
        verifyNoInteractions(reporter, scopedTables);
    }

    // ── regex fast-path ────────────────────────────────────────────────────

    @Test
    void predicateMatchShortCircuitsParser() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("SELECT * FROM pull_request WHERE workspace_id = ?");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void inPredicateMatchShortCircuits() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("SELECT * FROM pull_request WHERE workspace_id IN (?, ?)");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void compositeKeyTupleInPredicateShortCircuits() {
        // Hibernate emits composite-key lookups as
        //   (col1, workspace_id) IN ((?, ?))
        // The inspector MUST treat this as a legitimate workspace_id reference and not
        // throw. Regression for false positive that broke PracticeFindingControllerIntegrationTest.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        String sql =
            "select wm.user_id, wm.workspace_id from workspace_membership wm " +
            "where (wm.user_id, wm.workspace_id) in ((?,?))";
        inspector.inspect(sql);
        verifyNoInteractions(reporter, scopedTables);
    }

    // ── table extraction ───────────────────────────────────────────────────

    @Test
    void unguardedScopedTableIsReported() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("pull_request")).thenReturn(true);
        inspector.inspect("SELECT * FROM pull_request");
        verify(reporter).report("SELECT * FROM pull_request", Set.of("pull_request"), TenancyEnforcement.LOG);
    }

    @Test
    void globalTableQueryIsNotReported() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("user")).thenReturn(false);
        inspector.inspect("SELECT * FROM \"user\"");
        verify(reporter, never()).report(any(), any(), any());
    }

    @Test
    void schemaQualifiedTableIsUnqualifiedBeforeLookup() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("pull_request")).thenReturn(true);
        inspector.inspect("SELECT * FROM public.pull_request");
        verify(reporter).report("SELECT * FROM public.pull_request", Set.of("pull_request"), TenancyEnforcement.LOG);
    }

    @Test
    void backslashEscapedPostgresCastDoesNotPropagate() {
        // Regression: @Query native queries can contain Postgres casts like
        // CONCAT(:id\:\:text, ...). The inspector MUST NOT propagate exceptions on those —
        // would brick UserRepository.tryAcquireLoginLock and the whole context.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        String sql = "SELECT pg_try_advisory_xact_lock(hashtext(CONCAT(?\\:\\:text, ':', LOWER(?))))";
        String returned = inspector.inspect(sql);
        assertThat(returned).isEqualTo(sql);
        verify(reporter, never()).report(any(), any(), any());
    }

    // helper: Mockito.any() shorthand
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}

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
    void literalStringContainingWorkspaceIdFallsThroughToParser() {
        // Regression test for the regex false-negative loop 3 closed: the bare word
        // "workspace_id" in a string literal must NOT short-circuit past the parser.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("issue")).thenReturn(true);
        inspector.inspect("INSERT INTO issue (body) VALUES ('refers to workspace_id mapping')");
        verify(reporter).report(
            "INSERT INTO issue (body) VALUES ('refers to workspace_id mapping')",
            Set.of("issue"),
            TenancyEnforcement.LOG
        );
    }

    // ── parser slow path ───────────────────────────────────────────────────

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

    // ── parse failures ─────────────────────────────────────────────────────

    @Test
    void parseFailureIncrementsCounterAndPassesThrough() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        // Garbage SQL that JSqlParser cannot tokenise — exercises the fail-open branch.
        String garbage = "ANALYZE !!! not really sql ???";
        String returned = inspector.inspect(garbage);
        assertThat(returned).isEqualTo(garbage);
        verify(parseFailureCounter).increment();
        verify(reporter, never()).report(any(), any(), any());
    }

    // helper: Mockito.any() shorthand
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}

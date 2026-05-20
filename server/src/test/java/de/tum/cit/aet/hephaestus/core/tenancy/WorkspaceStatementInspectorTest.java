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

    // ── DML semantics ──────────────────────────────────────────────────────

    @Test
    void insertOnScopedTableIsAllowed() {
        // INSERTs cannot leak existing data across workspaces. The workspace_id (or FK
        // chain) is placed into the row by application code, not enforced by the inspector.
        // Regression: practice_finding/finding_feedback inserts emitted at Hibernate flush
        // time triggered TenancyViolationException despite being safe by construction.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("insert into practice_finding (id, title, practice_id) values (?, ?, ?)");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void pkOnlyDeleteOnScopedTableIsAllowed() {
        // Hibernate emits delete-by-id for delete(entity)/deleteById(id). The row was
        // loaded into the persistence context within a workspace-checked transaction;
        // requiring an additional workspace_id predicate would force @WorkspaceAgnostic
        // onto every repository.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("delete from practice where id=?");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void pkOnlyUpdateOnScopedTableIsAllowed() {
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("update practice set name=?, updated_at=? where id=?");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void deleteJoinTableByParentFkIsAllowed() {
        // @ManyToMany cascade-delete on the join table when a parent is removed:
        // Hibernate emits DELETE FROM join_table WHERE <parent>_id = ?. Safe — the caller
        // already had the parent's PK (workspace-scoped at retrieval).
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("delete from issue_blocking where blocked_issue_id=?");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void optimisticLockUpdateIsAllowed() {
        // Hibernate appends "AND version = ?" to UPDATEs for entities with @Version.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(
            "update chat_message set metadata=?,parts=?,role=?,status=?,thread_id=?,version=? where id=? and version=?"
        );
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void deleteWithExtraPredicateStillRequiresWorkspaceId() {
        // PK-only allowance MUST NOT extend to multi-column deletes — those are likely
        // hand-written queries where we still want the workspace_id discipline.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("practice")).thenReturn(true);
        inspector.inspect("delete from practice where id=? and slug=?");
        verify(reporter).report(
            "delete from practice where id=? and slug=?",
            Set.of("practice"),
            TenancyEnforcement.LOG
        );
    }

    @Test
    void hibernateEntityLoadByPkIsAllowed() {
        // Hibernate-emitted entity load / lazy fetch SQL: SELECT ... FROM table alias
        // WHERE alias.id = ?. Safe because the caller already had the surrogate PK.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(
            "select r1_0.id,r1_0.archived,r1_0.created_at,r1_0.html_url,r1_0.name " +
                "from repository r1_0 where r1_0.id=?"
        );
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void hibernateEntityLoadByPkWithJoinsIsAllowed() {
        // Eager @ManyToOne fetches add LEFT JOINs but the WHERE is still <alias>.id = ?.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(
            "select r1_0.id,o1_0.id,o1_0.login " +
                "from repository r1_0 " +
                "left join organization o1_0 on r1_0.organization_id=o1_0.id " +
                "where r1_0.id=?"
        );
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void selectWithoutPkPredicateStillEnforced() {
        // Don't let PK-only allowance leak into broader SELECT patterns.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.LOG);
        when(scopedTables.isScoped("pull_request")).thenReturn(true);
        inspector.inspect("select pr1_0.id from pull_request pr1_0 where pr1_0.state=?");
        verify(reporter).report(
            "select pr1_0.id from pull_request pr1_0 where pr1_0.state=?",
            Set.of("pull_request"),
            TenancyEnforcement.LOG
        );
    }

    @Test
    void hibernateCollectionLoadByParentFkIsAllowed() {
        // Hibernate-emitted @ManyToMany collection initialisation queries the join table
        // filtered by the parent's FK column. The join table inherits scope from the
        // parent's PK, which the caller already had.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(
            "select i1_0.label_id,i1_1.id,i1_1.body,i1_1.title " +
                "from issue_label i1_0 " +
                "join issue i1_1 on i1_0.issue_id=i1_1.id " +
                "where i1_0.label_id=?"
        );
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void hibernateOneToManyByParentFkIsAllowed() {
        // @OneToMany owned by the child via FK: SELECT c.* FROM comment c WHERE c.issue_id = ?
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect("select c1_0.id,c1_0.body from issue_comment c1_0 where c1_0.issue_id=?");
        verifyNoInteractions(reporter, scopedTables);
    }

    @Test
    void hibernateSingleTableInheritanceLoadIsAllowed() {
        // Single-table inheritance: SELECT … FROM issue alias WHERE alias.id = ? AND
        // alias.<discriminator> = '<subtype>'. The PK predicate still pins the result set.
        WorkspaceStatementInspector inspector = newInspector(TenancyEnforcement.THROW);
        inspector.inspect(
            "select pr1_0.id,pr1_0.body,pr1_0.title " +
                "from issue pr1_0 " +
                "where pr1_0.id=? and pr1_0.issue_type='PullRequest'"
        );
        verifyNoInteractions(reporter, scopedTables);
    }

    // helper: Mockito.any() shorthand
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}

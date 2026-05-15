package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.ChatMessage;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration coverage for {@link MentorTurnPersistence}. Validates the REQUIRES_NEW
 * transactional contract end-to-end against a real Postgres container, covering the
 * code paths the unit tests cannot exercise (DB unique partial index, JSONB metadata
 * round-trip, status transitions, reaper sweep).
 */
// Test profile disables mentor by default (see application-test.yml); opt back in here so the
// MentorTurnPersistence + dependent beans are registered. The @MockitoBean below provides the
// sandbox SPI dependency that DockerSandboxConfiguration would otherwise have to supply.
@TestPropertySource(properties = "hephaestus.mentor.enabled=true")
@DisplayName("MentorTurnPersistence integration")
class MentorTurnPersistenceIntegrationTest extends BaseIntegrationTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Autowired
    private MentorTurnPersistence persistence;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private DataSource dataSource;

    /**
     * {@link MentorChatService} pulls in this collaborator unconditionally; the production bean
     * is only registered when {@code hephaestus.sandbox.enabled=true} (Docker required). Provide
     * a mock so the integration context loads — this test never touches the sandbox boundary.
     */
    @MockitoBean
    @SuppressWarnings("unused")
    private InteractiveSandboxService interactiveSandboxService;

    private Workspace workspace;
    private User user;

    /**
     * Path to the consolidated migration. The {@link #migrationDeclaresInFlightUniqueIndex}
     * test asserts the migration file IS the source of truth for the DDL this integration
     * test exercises. If the migration changes the index name, predicate, or columns, that
     * test fails — closing the gap where the previous test re-created the index manually and
     * silently tolerated migration regressions.
     */
    private static final java.nio.file.Path MIGRATION_PATH = java.nio.file.Path.of(
        "src/main/resources/db/changelog/1778756946278_changelog.xml"
    );

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        // The test profile uses JPA ddl-auto=create — Liquibase is disabled, so partial
        // indexes / CHECK constraints (which JPA can't infer from @Entity) never land. We
        // create them here so persistence-layer tests have the real production DDL to fail
        // against. Migration ID references kept in sync via the `migrationDeclaresInFlightUniqueIndex`
        // test below.
        //
        // The wave-2 status-column migration replaced the JSONB-keyed unique index +
        // metadata-status CHECK with a column-keyed index + column CHECK. We mirror that
        // shape here so tests exercise the production constraint shape, not the legacy one.
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_message_in_flight_v2 " +
                    "ON chat_message (thread_id) WHERE status = 'in_flight'"
            );
            // Drop-then-add so a previous run's constraint doesn't survive across tests.
            stmt.execute("ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_status");
            stmt.execute(
                "ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_status " +
                    "CHECK (status IN ('in_flight', 'completed', 'interrupted'))"
            );
        }
        workspace = new Workspace();
        workspace.setWorkspaceSlug("mentor-persist-ws");
        workspace.setDisplayName("Mentor Persistence Workspace");
        workspace.setAccountLogin("mentor-persist-org");
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaceRepository.save(workspace);

        GitProvider gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.com")));

        user = new User();
        user.setNativeId(7_001L);
        user.setLogin("mentor-tester");
        user.setName("Mentor Tester");
        user.setAvatarUrl("https://example.com/m.png");
        user.setHtmlUrl("https://gitlab.com/mentor-tester");
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setProvider(gitProvider);
        user = userRepository.save(user);
    }

    @Test
    @DisplayName("ensureThread creates a thread when absent")
    void ensureThread_createsWhenAbsent() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = persistence.ensureThread(workspace.getId(), threadId, user, "Hello mentor");
        assertThat(thread.getId()).isEqualTo(threadId);
        assertThat(thread.getUser().getId()).isEqualTo(user.getId());
        assertThat(thread.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(thread.getTitle()).isEqualTo("Hello mentor");
        assertThat(chatThreadRepository.findById(threadId)).isPresent();
    }

    @Test
    @DisplayName("ensureThread returns the existing row when one already exists")
    void ensureThread_returnsExisting() {
        UUID threadId = UUID.randomUUID();
        ChatThread first = persistence.ensureThread(workspace.getId(), threadId, user, "first prompt");
        ChatThread second = persistence.ensureThread(workspace.getId(), threadId, user, "second prompt");
        assertThat(second.getId()).isEqualTo(first.getId());
        // Title is fixed on first write — a second call with a different prompt must NOT
        // overwrite, otherwise the thread sidebar flickers between titles.
        assertThat(second.getTitle()).isEqualTo("first prompt");
    }

    @Test
    @DisplayName("ensureThread hides foreign-owner reads as 404 (no thread enumeration)")
    void ensureThread_foreignOwnerThrows() {
        UUID threadId = UUID.randomUUID();
        persistence.ensureThread(workspace.getId(), threadId, user, "hello");

        User other = new User();
        other.setNativeId(7_002L);
        other.setLogin("other");
        other.setName("Other");
        other.setAvatarUrl("https://example.com/o.png");
        other.setHtmlUrl("https://gitlab.com/other");
        other.setType(User.Type.USER);
        other.setCreatedAt(Instant.now());
        other.setUpdatedAt(Instant.now());
        other.setProvider(user.getProvider());
        other = userRepository.save(other);

        final User otherUser = other;
        assertThatThrownBy(() ->
            persistence.ensureThread(workspace.getId(), threadId, otherUser, "intruder")
        ).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("persistInFlight writes user+assistant rows, returns assistant id, status=in_flight")
    void persistInFlight_happyPath() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello mentor",
            assistantId,
            null
        );
        assertThat(cookie.assistantMessageId()).isEqualTo(assistantId);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.in_flight);

        ChatMessage userMessage = chatMessageRepository.findById(cookie.userMessageId()).orElseThrow();
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts().get(0).path("text").asText()).isEqualTo("hello mentor");
    }

    @Test
    @DisplayName("persistInFlight honours the client-supplied user message id")
    void persistInFlight_honorsClientUserMessageId() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID clientUserId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello mentor",
            assistantId,
            clientUserId
        );
        assertThat(cookie.userMessageId()).isEqualTo(clientUserId);
        assertThat(chatMessageRepository.findById(clientUserId)).isPresent();
    }

    @Test
    @DisplayName("persistInFlight throws TurnAlreadyInFlightException on the DB unique partial index")
    void persistInFlight_secondCallThrows() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        persistence.persistInFlight(thread, "first", UUID.randomUUID(), null);
        assertThatThrownBy(() -> persistence.persistInFlight(thread, "second", UUID.randomUUID(), null)).isInstanceOf(
            TurnAlreadyInFlightException.class
        );
    }

    @Test
    @DisplayName("persistInFlight: TWO REAL THREADS race the same thread row — exactly one wins, exactly one 409s")
    void persistInFlight_concurrentRace_exactlyOneWins() throws Exception {
        // Sequential calls only prove the SQL `WHERE in_flight` semantics fire. The dual-lock
        // defence (Java MentorTurnLock + DB partial unique index) is supposed to handle the
        // case where two API replicas (or two virtual threads on one replica) race the same
        // thread id. This test proves the DB half: even if the Java lock were stripped,
        // exactly one writer wins at the row level.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch fire = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Object> attempt = () -> {
                ready.countDown();
                fire.await(5, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    return persistence.persistInFlight(thread, "race", UUID.randomUUID(), null);
                } catch (RuntimeException ex) {
                    return ex; // surface to caller for classification
                }
            };
            var fa = pool.submit(attempt);
            var fb = pool.submit(attempt);
            // Wait until both threads are at the barrier, then release simultaneously.
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            Object resultA = fa.get(10, java.util.concurrent.TimeUnit.SECONDS);
            Object resultB = fb.get(10, java.util.concurrent.TimeUnit.SECONDS);

            int winners = 0;
            int conflicts = 0;
            for (Object r : java.util.List.of(resultA, resultB)) {
                if (r instanceof MentorTurnPersistence.TurnPersistenceCookie) {
                    winners++;
                } else if (r instanceof TurnAlreadyInFlightException) {
                    conflicts++;
                } else if (r instanceof Throwable t) {
                    // Some Postgres drivers wrap the constraint violation in a different
                    // unchecked exception (DataIntegrityViolationException, etc.). Treat any
                    // non-cookie outcome as a conflict so long as no other type leaked through.
                    if (
                        t.getClass().getSimpleName().contains("DataIntegrity") ||
                        t.getCause() instanceof TurnAlreadyInFlightException
                    ) {
                        conflicts++;
                    } else {
                        throw new AssertionError("Unexpected exception type: " + t, t);
                    }
                }
            }
            assertThat(winners).as("exactly one writer succeeds").isEqualTo(1);
            assertThat(conflicts).as("exactly one writer 409s").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("finalise flips status to completed and writes parts + usage")
    void finalise_writesCompletedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

        TranslatorState state = new TranslatorState(assistantId);
        state.observeModel("openai/gpt-oss-120b");
        ObjectNode usage = NODES.objectNode();
        usage.put("input", 123).put("output", 45);
        state.observeUsage(usage);
        // Open + close a text block so partsSnapshot() has something to write.
        state.openTextBlock("text-0");
        state.appendText("Hello there!");
        state.closeTextBlock();

        UIMessageChunk.FinishMetadata finishMeta = new UIMessageChunk.FinishMetadata(
            "openai/gpt-oss-120b",
            new UIMessageChunk.FinishMetadata.Usage(123, 45, null, null, 168),
            /* costUsd */ null
        );
        UIMessageChunk.Finish finish = new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, finishMeta);

        persistence.finalise(cookie, state, finish);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.completed);
        JsonNode meta = assistant.getMetadata();
        assertThat(meta.path("finishReason").asText()).isEqualTo("stop");
        assertThat(meta.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(meta.path("inputTokens").asLong()).isEqualTo(123);
        assertThat(meta.path("outputTokens").asLong()).isEqualTo(45);
        assertThat(assistant.getParts().isArray()).isTrue();
        assertThat(assistant.getParts().get(0).path("text").asText()).isEqualTo("Hello there!");
    }

    @Test
    @DisplayName("interrupt flips status to interrupted and stores the error message")
    void interrupt_writesInterruptedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            "hello",
            assistantId,
            null
        );

        persistence.interrupt(cookie, new TranslatorState(assistantId), new IllegalStateException("upstream timeout"));

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(assistant.getMetadata().path("error").asText()).isEqualTo("upstream timeout");
    }

    @Test
    @DisplayName("@Version: reaper bumps version, then a stale-snapshot save throws OptimisticLockingFailureException")
    void optimisticLocking_staleSnapshotSaveFails() {
        // Root-cause protection against the reaper-vs-late-finalise data corruption: a
        // writer that loaded the entity at version=N can no longer overwrite a row the
        // reaper bumped to N+1. Hibernate detects the version mismatch on the SQL UPDATE
        // predicate (`WHERE id = ? AND version = ?`) and throws — the persistence service
        // catches and skips, so the reaper's verdict survives.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, "hello", assistantId, null);

        // Simulate the in-flight runner: load a managed snapshot at the current version.
        ChatMessage stale = chatMessageRepository.findById(assistantId).orElseThrow();
        Long versionBefore = stale.getVersion();
        assertThat(versionBefore).isNotNull();

        // Reaper sweeps, bumps version explicitly via @Modifying SQL.
        chatMessageRepository.reapStaleInFlight(Instant.now().plusSeconds(60));
        ChatMessage afterReaper = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(afterReaper.getVersion()).isEqualTo(versionBefore + 1L);
        assertThat(afterReaper.getStatus()).isEqualTo(ChatMessage.Status.interrupted);

        // Stale-snapshot save attempt: Hibernate's UPDATE predicate fails on the version,
        // surfaces as OptimisticLockingFailureException. A prior read-before-write
        // `isStillInFlight` check is replaced by this DB-enforced protection — version
        // mismatch is the single durable signal that another writer touched the row.
        stale.setStatus(ChatMessage.Status.completed);
        assertThatThrownBy(() -> {
            chatMessageRepository.saveAndFlush(stale);
        }).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        // After the failed save attempt, the row in the DB still reflects the reaper's verdict.
        ChatMessage finalState = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(finalState.getMetadata().path("error").asText()).isEqualTo("server restart");
    }

    @Test
    @DisplayName("reapStaleInFlight flips in-flight rows older than the cutoff")
    void reaper_flipsStaleInFlightRows() throws Exception {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "stuck");
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, "stuck", assistantId, null);

        // The cutoff is "older than now" — fresh rows are NOT reaped.
        int updated = chatMessageRepository.reapStaleInFlight(Instant.now().minusSeconds(60));
        assertThat(updated).isZero();
        assertThat(chatMessageRepository.findById(assistantId).orElseThrow().getStatus()).isEqualTo(
            ChatMessage.Status.in_flight
        );

        // Cutoff in the future → row is past the cutoff → reaped.
        int updatedSweep = chatMessageRepository.reapStaleInFlight(Instant.now().plusSeconds(60));
        assertThat(updatedSweep).isEqualTo(1);
        ChatMessage reaped = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(reaped.getStatus()).isEqualTo(ChatMessage.Status.interrupted);
        assertThat(reaped.getMetadata().path("error").asText()).isEqualTo("server restart");
    }

    @Test
    @DisplayName("migration: declares both the legacy and v2 in-flight unique partial indexes")
    void migrationDeclaresInFlightUniqueIndex() throws Exception {
        // The test profile disables Liquibase, so this suite re-creates the in-flight partial
        // index in @BeforeEach. That bypass is acceptable ONLY if the production migration
        // changeset stays the source of truth — a future migration that renames the index,
        // changes the predicate, or drops the changeset would otherwise silently sail past
        // every integration test and break production. This guard fails first.
        //
        // Wave-2 added the `_v2` variant keyed on the column instead of metadata->>'status'.
        // The original changeset still ships for rollback symmetry; the v2 takes over after
        // backfill. Assert both shapes are declared.
        String migration = java.nio.file.Files.readString(MIGRATION_PATH);
        assertThat(migration)
            .as("migration must contain the legacy + v2 in-flight unique-index changesets")
            .contains("mentor-1071-in-flight-unique-index")
            .contains("mentor-1071-replace-in-flight-index");
        assertThat(migration)
            .as("legacy index uses metadata->>'status' predicate; v2 uses status column")
            .contains("ux_chat_message_in_flight")
            .contains("ux_chat_message_in_flight_v2")
            .contains("CONCURRENTLY")
            .contains("'in_flight'");
        // Status column promotion: backfill + NOT NULL + CHECK must all be declared.
        assertThat(migration)
            .as("migration must declare the status column + backfill + NOT NULL + enum CHECK")
            .contains("mentor-1071-add-status-column")
            .contains("mentor-1071-backfill-status")
            .contains("mentor-1071-status-not-null-and-check")
            .contains("chk_chat_message_status");
        // @Version column is wired by `mentor-1071-add-version-column` — same lockstep
        // guarantee.
        assertThat(migration)
            .as("migration must declare the @Version column for optimistic locking")
            .contains("mentor-1071-add-version-column")
            .contains("name=\"version\"")
            .contains("type=\"BIGINT\"");
    }

    @Test
    @DisplayName("chk_chat_message_status rejects values outside (in_flight,completed,interrupted)")
    void statusColumnCheckConstraintFires() throws Exception {
        // Pin the production DB-level guard. The column CHECK is the only backstop that
        // catches a future writer typo'ing `"in_flite"` or inventing a new status without a
        // matching application-side state machine update. The integration test profile
        // recreates the constraint in @BeforeEach so this assertion exercises the real
        // predicate, not just a JPA enum validation.
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "constraint test");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            try (
                var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement(
                    "INSERT INTO chat_message (id, thread_id, role, parts, status, created_at, version) " +
                        "VALUES (?, ?, 'ASSISTANT', '[]'::jsonb, ?, now(), 0)"
                )
            ) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, thread.getId());
                // Must fit VARCHAR(16) so we exercise the CHECK constraint, not the
                // length truncation that fires before the CHECK runs.
                stmt.setString(3, "in_flite");
                stmt.executeUpdate();
            }
        })
            .isInstanceOf(java.sql.SQLException.class)
            // The constraint name varies by environment: production ships our explicit
            // `chk_chat_message_status` via Liquibase, while the test profile's ddl-auto=create
            // also generates a Hibernate-implicit `chat_message_status_check` from the
            // @Enumerated(EnumType.STRING) field. Either fires first — both encode the same
            // enum invariant — so the assertion accepts either.
            .satisfies(t ->
                org.assertj.core.api.Assertions.assertThat(t.getMessage()).containsAnyOf(
                    "chk_chat_message_status",
                    "chat_message_status_check"
                )
            );
    }
}

package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
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
import de.tum.in.www1.hephaestus.mentor.chat.wire.UIMessageChunk;
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
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integration coverage for {@link MentorTurnPersistence}. Validates the REQUIRES_NEW
 * transactional contract end-to-end against a real Postgres container, covering the
 * code paths the unit tests cannot exercise (DB unique partial index, JSONB metadata
 * round-trip, status transitions, reaper sweep).
 */
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
    @MockBean
    @SuppressWarnings("unused")
    private InteractiveSandboxService interactiveSandboxService;

    private Workspace workspace;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        // The test profile uses JPA ddl-auto=create — Liquibase is disabled, so the unique
        // partial index on chat_message(thread_id) WHERE status='in_flight' (migration
        // 1779000000004) never lands. Create it here so the contract under test is real.
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_message_in_flight " +
                    "ON chat_message (thread_id) WHERE (metadata ->> 'status') = 'in_flight'"
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
            assistantId
        );
        assertThat(cookie.assistantMessageId()).isEqualTo(assistantId);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        assertThat(assistant.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistant.getMetadata().path("status").asText()).isEqualTo("in_flight");

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
        persistence.persistInFlight(thread, "first", UUID.randomUUID());
        assertThatThrownBy(() -> persistence.persistInFlight(thread, "second", UUID.randomUUID())).isInstanceOf(
            TurnAlreadyInFlightException.class
        );
    }

    @Test
    @DisplayName("finalise flips status to completed and writes parts + usage")
    void finalise_writesCompletedRow() {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "hello");
        UUID assistantId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(thread, "hello", assistantId);

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
        UIMessageChunk.Finish finish = new UIMessageChunk.Finish("stop", finishMeta);

        persistence.finalise(cookie, state, finish, null);

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        JsonNode meta = assistant.getMetadata();
        assertThat(meta.path("status").asText()).isEqualTo("completed");
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
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(thread, "hello", assistantId);

        persistence.interrupt(cookie, new TranslatorState(assistantId), new IllegalStateException("upstream timeout"));

        ChatMessage assistant = chatMessageRepository.findById(assistantId).orElseThrow();
        JsonNode meta = assistant.getMetadata();
        assertThat(meta.path("status").asText()).isEqualTo("interrupted");
        assertThat(meta.path("error").asText()).isEqualTo("upstream timeout");
    }

    @Test
    @DisplayName("reapStaleInFlight flips in-flight rows older than the cutoff")
    void reaper_flipsStaleInFlightRows() throws Exception {
        ChatThread thread = persistence.ensureThread(workspace.getId(), UUID.randomUUID(), user, "stuck");
        UUID assistantId = UUID.randomUUID();
        persistence.persistInFlight(thread, "stuck", assistantId);

        // The cutoff is "older than now" — fresh rows are NOT reaped.
        int updated = chatMessageRepository.reapStaleInFlight(Instant.now().minusSeconds(60));
        assertThat(updated).isZero();
        assertThat(
            chatMessageRepository.findById(assistantId).orElseThrow().getMetadata().path("status").asText()
        ).isEqualTo("in_flight");

        // Cutoff in the future → row is past the cutoff → reaped.
        int updatedSweep = chatMessageRepository.reapStaleInFlight(Instant.now().plusSeconds(60));
        assertThat(updatedSweep).isEqualTo(1);
        JsonNode meta = chatMessageRepository.findById(assistantId).orElseThrow().getMetadata();
        assertThat(meta.path("status").asText()).isEqualTo("interrupted");
        assertThat(meta.path("error").asText()).isEqualTo("server restart");
    }
}

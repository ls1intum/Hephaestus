package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Service-layer unit tests. Focused on the two behaviours that have wire-level consequences:
 * owner scoping (controllers rely on a 404 — not a silent leak — for cross-user reads) and
 * the dual-write fallback for {@code effectiveParts} (drops once #1074 lands).
 */
@DisplayName("ChatThreadService")
class ChatThreadServiceTest extends BaseUnitTest {

    @Mock
    private ChatThreadRepository chatThreadRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    private ChatThreadService service;

    private static final Long WORKSPACE_ID = 42L;
    private static final Long OWNER_USER_ID = 7L;
    private static final Long OTHER_USER_ID = 99L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ChatThreadService(chatThreadRepository, chatMessageRepository, userRepository, objectMapper);
    }

    @Test
    @DisplayName("getOwnedThread → 404-style EntityNotFound when thread missing")
    void getOwnedThread_missingThreadThrows() {
        UUID threadId = UUID.randomUUID();
        when(userRepository.getCurrentUserElseThrow()).thenReturn(stubUser(OWNER_USER_ID));
        when(chatThreadRepository.findByIdAndWorkspaceId(threadId, WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownByEnityNotFound(() -> service.getOwnedThread(WORKSPACE_ID, threadId));
    }

    @Test
    @DisplayName("getOwnedThread → 404-style EntityNotFound when thread owned by a different user")
    void getOwnedThread_foreignOwnerHidden() {
        UUID threadId = UUID.randomUUID();
        ChatThread thread = stubThread(threadId, stubUser(OTHER_USER_ID));
        when(userRepository.getCurrentUserElseThrow()).thenReturn(stubUser(OWNER_USER_ID));
        when(chatThreadRepository.findByIdAndWorkspaceId(threadId, WORKSPACE_ID)).thenReturn(Optional.of(thread));

        // We expose foreign threads as 404, not 403 — non-owners must not learn of existence.
        assertThatThrownByEnityNotFound(() -> service.getOwnedThread(WORKSPACE_ID, threadId));
    }

    @Test
    @DisplayName("getOwnedThread returns the entity when ownership matches")
    void getOwnedThread_returnsForOwner() {
        UUID threadId = UUID.randomUUID();
        User owner = stubUser(OWNER_USER_ID);
        ChatThread thread = stubThread(threadId, owner);
        when(userRepository.getCurrentUserElseThrow()).thenReturn(owner);
        when(chatThreadRepository.findByIdAndWorkspaceId(threadId, WORKSPACE_ID)).thenReturn(Optional.of(thread));

        ChatThread resolved = service.getOwnedThread(WORKSPACE_ID, threadId);

        assertThat(resolved).isSameAs(thread);
    }

    @Test
    @DisplayName("effectiveParts: JSONB column wins when populated")
    void effectiveParts_prefersJsonbColumn() {
        ChatMessage msg = new ChatMessage();
        ArrayNode parts = JsonNodeFactory.instance.arrayNode();
        parts.add(JsonNodeFactory.instance.objectNode().put("type", "text").put("text", "hello"));
        msg.setParts(parts);

        JsonNode resolved = service.effectiveParts(msg);

        assertThat(resolved).isSameAs(parts);
    }

    @Test
    @DisplayName("effectiveParts: rebuilds from legacyParts when JSONB column is empty")
    void effectiveParts_rebuildsFromLegacyParts() {
        ChatMessage msg = new ChatMessage();
        // parts is null/empty array → fallback engaged

        ChatMessagePart legacyText = stubLegacyPart(ChatMessagePart.PartType.TEXT, "text", "{\"text\":\"hi\"}");
        ChatMessagePart legacyTool = stubLegacyPart(
            ChatMessagePart.PartType.TOOL,
            "tool-fetch_context",
            "{\"toolCallId\":\"call-1\",\"state\":\"output-available\"}"
        );
        msg.setLegacyParts(List.of(legacyText, legacyTool));

        JsonNode rebuilt = service.effectiveParts(msg);

        assertThat(rebuilt.isArray()).isTrue();
        assertThat(rebuilt).hasSize(2);
        assertThat(rebuilt.get(0).get("type").asText()).isEqualTo("text");
        assertThat(rebuilt.get(0).get("text").asText()).isEqualTo("hi");
        // Tool part keeps its originalType discriminator (NOT the enum.value() fallback) so
        // AI SDK clients receive the canonical `tool-<name>` literal Pi emits.
        assertThat(rebuilt.get(1).get("type").asText()).isEqualTo("tool-fetch_context");
        assertThat(rebuilt.get(1).get("toolCallId").asText()).isEqualTo("call-1");
    }

    @Test
    @DisplayName("effectiveParts: returns empty array when both shapes are empty")
    void effectiveParts_emptyWhenNeitherSet() {
        ChatMessage msg = new ChatMessage();

        JsonNode result = service.effectiveParts(msg);

        assertThat(result.isArray()).isTrue();
        assertThat(result).isEmpty();
    }

    private static User stubUser(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private ChatThread stubThread(UUID id, User owner) {
        ChatThread thread = new ChatThread();
        thread.setId(id);
        thread.setUser(owner);
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        thread.setWorkspace(workspace);
        return thread;
    }

    private ChatMessagePart stubLegacyPart(ChatMessagePart.PartType type, String originalType, String contentJson) {
        ChatMessagePart part = new ChatMessagePart();
        part.setType(type);
        part.setOriginalType(originalType);
        try {
            part.setContent(objectMapper.readTree(contentJson));
        } catch (Exception e) {
            throw new AssertionError("Test fixture JSON should be parseable", e);
        }
        return part;
    }

    private static void assertThatThrownByEnityNotFound(Runnable runnable) {
        org.assertj.core.api.Assertions.assertThatThrownBy(runnable::run).isInstanceOf(EntityNotFoundException.class);
    }
}

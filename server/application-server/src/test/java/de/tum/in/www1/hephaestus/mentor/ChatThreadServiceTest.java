package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Service-layer unit tests. Pin the workspace + owner scoping the controllers rely on for
 * 404-vs-leak semantics.
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

    private static void assertThatThrownByEnityNotFound(Runnable runnable) {
        org.assertj.core.api.Assertions.assertThatThrownBy(runnable::run).isInstanceOf(EntityNotFoundException.class);
    }
}

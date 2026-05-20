package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.gitprovider.user.User;
import de.tum.cit.aet.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Read/write paths over {@link ChatThread} that enforce workspace + owner scoping at the
 * service boundary. Controllers must never bypass these methods to talk to the repository
 * directly — the workspace/owner gate is the only guard against cross-user thread access.
 */
@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** Thread summaries (id, title, createdAt) owned by the current user, newest first. */
    @Transactional(readOnly = true)
    public List<ChatThreadSummaryDTO> listSummariesForCurrentUser(Long workspaceId) {
        User user = userRepository.getCurrentUserElseThrow();
        return chatThreadRepository.findSummariesByWorkspaceAndUser(workspaceId, user.getId());
    }

    /**
     * Load a thread within the workspace; throws {@link EntityNotFoundException} if the
     * thread does not exist OR is not owned by the current user (404, not 403 — we don't
     * confirm/deny existence to non-owners).
     */
    @Transactional(readOnly = true)
    public ChatThread getOwnedThread(Long workspaceId, UUID threadId) {
        User user = userRepository.getCurrentUserElseThrow();
        ChatThread thread = chatThreadRepository
            .findByIdAndWorkspaceId(threadId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("ChatThread", threadId.toString()));
        if (thread.getUser() == null || !thread.getUser().getId().equals(user.getId())) {
            throw new EntityNotFoundException("ChatThread", threadId.toString());
        }
        return thread;
    }

    /** Delete a thread (cascades to messages, votes). Owner-scoped via {@link #getOwnedThread}. */
    @Transactional
    public void deleteOwnedThread(Long workspaceId, UUID threadId) {
        chatThreadRepository.delete(getOwnedThread(workspaceId, threadId));
    }

    /**
     * Single read-only transaction that resolves the thread, its messages, and converts each
     * to a DTO. {@code chat_message.parts} is the canonical JSONB representation; backfill
     * guarantees every row has a non-empty array.
     */
    @Transactional(readOnly = true)
    public ThreadDetail loadOwnedThreadDetail(Long workspaceId, UUID threadId) {
        ChatThread thread = getOwnedThread(workspaceId, threadId);
        List<ChatMessageDTO> messages = thread
            .getAllMessages()
            .stream()
            .map(msg -> ChatMessageDTO.from(msg, msg.getParts(), objectMapper))
            .toList();
        return new ThreadDetail(thread.getId(), thread.getTitle(), thread.getCreatedAt(), messages);
    }

    /** Snapshot of a thread + messages in DTO form. */
    public record ThreadDetail(UUID id, String title, java.time.Instant createdAt, List<ChatMessageDTO> messages) {}
}

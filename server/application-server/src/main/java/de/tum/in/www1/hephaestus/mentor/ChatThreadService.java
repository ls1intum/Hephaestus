package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write paths over {@link ChatThread} that enforce workspace + owner scoping at the
 * service boundary. Controllers must never bypass these methods to talk to the repository
 * directly — the workspace/owner gate is the only guard against cross-user thread access.
 *
 * <p>Until {@code chat_message_part} is dropped in #1074, the service resolves message parts
 * via {@link #effectiveParts(ChatMessage)} which prefers the JSONB column and falls back to
 * the legacy normalised rows. New writers always populate the JSONB column directly.
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

    /** Delete a thread (cascades to messages, votes, legacy parts). Owner-scoped via {@link #getOwnedThread}. */
    @Transactional
    public void deleteOwnedThread(Long workspaceId, UUID threadId) {
        chatThreadRepository.delete(getOwnedThread(workspaceId, threadId));
    }

    /**
     * Single read-only transaction that resolves the thread, its messages, and converts each to
     * a DTO. Folds the {@code effectiveParts} fallback inside the txn so legacy-parts lazy reads
     * happen against an open session. The {@code legacyParts} association carries
     * {@code @BatchSize(50)} (see {@link ChatMessage#legacyParts}) so the fallback path resolves
     * the legacy rows for every message in a single batched fetch instead of N round trips.
     */
    @Transactional(readOnly = true)
    public ThreadDetail loadOwnedThreadDetail(Long workspaceId, UUID threadId) {
        ChatThread thread = getOwnedThread(workspaceId, threadId);
        List<ChatMessageDTO> messages = thread
            .getAllMessages()
            .stream()
            .map(msg -> ChatMessageDTO.from(msg, effectiveParts(msg), objectMapper))
            .toList();
        return new ThreadDetail(thread.getId(), thread.getTitle(), thread.getCreatedAt(), messages);
    }

    /** Snapshot of a thread + messages in DTO form. */
    public record ThreadDetail(UUID id, String title, java.time.Instant createdAt, List<ChatMessageDTO> messages) {}

    /**
     * Returns the message parts in AI SDK UIMessage shape. Prefers the JSONB column;
     * falls back to reconstructing from {@link ChatMessagePart} rows during the
     * dual-write window. Always returns a JSON array (possibly empty).
     */
    public JsonNode effectiveParts(ChatMessage message) {
        JsonNode parts = message.getParts();
        if (parts != null && parts.isArray() && parts.size() > 0) {
            return parts;
        }
        // Fallback: rebuild from legacy normalised rows (sorted by orderIndex via @OrderBy).
        ArrayNode out = objectMapper.createArrayNode();
        for (ChatMessagePart legacy : message.getLegacyParts()) {
            ObjectNode part =
                legacy.getContent() != null && legacy.getContent().isObject()
                    ? ((ObjectNode) legacy.getContent()).deepCopy()
                    : objectMapper.createObjectNode();
            String typeString =
                legacy.getOriginalType() != null ? legacy.getOriginalType() : legacy.getType().getValue();
            part.put("type", typeString);
            out.add(part);
        }
        return out;
    }
}

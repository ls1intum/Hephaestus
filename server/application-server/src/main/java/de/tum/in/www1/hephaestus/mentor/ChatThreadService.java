package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing chat threads.
 * Provides business logic for thread operations and user authorization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Get thread summaries for a specific user.
     * Returns basic thread information without loading full message content.
     *
     * @param user the authenticated user
     * @return List of thread summaries ordered by creation date (newest first)
     */
    public List<ChatThreadSummaryDTO> getThreadSummariesForUser(User user) {
        log.debug("Getting thread summaries for user: {}", user.getLogin());

        List<ChatThread> threads = chatThreadRepository.findByUserOrderByCreatedAtDesc(user);

        return threads.stream().map(this::convertToSummaryDTO).collect(Collectors.toList());
    }

    /**
     * Get thread summaries for a specific user grouped by time periods.
     * Returns threads organized into groups using a single efficient database query.
     *
     * @param user the authenticated user
     * @return List of thread groups ordered by time relevance
     */
    public List<ChatThreadGroupDTO> getGroupedThreadSummariesForUser(User user) {
        log.debug("Getting grouped thread summaries for user: {}", user.getLogin());

        // Get all threads for the user in a single query, ordered by creation date
        List<ChatThread> threads = chatThreadRepository.findByUserOrderByCreatedAtDesc(user);

        if (threads.isEmpty()) {
            return new ArrayList<>();
        }

        // Group threads by time periods in memory (single pass)
        Map<String, List<ChatThreadSummaryDTO>> groupedThreads = new LinkedHashMap<>();

        // Define time boundaries once
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate yesterday = today.minusDays(1);
        LocalDate sevenDaysAgo = today.minusDays(7);
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate ninetyDaysAgo = today.minusDays(90);

        // Single pass through threads to group them
        for (ChatThread thread : threads) {
            ChatThreadSummaryDTO summaryDTO = convertToSummaryDTO(thread);
            LocalDate threadDate = thread.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();

            String groupName;
            if (threadDate.equals(today)) {
                groupName = "Today";
            } else if (threadDate.equals(yesterday)) {
                groupName = "Yesterday";
            } else if (threadDate.isAfter(sevenDaysAgo)) {
                groupName = "Last 7 Days";
            } else if (threadDate.isAfter(thirtyDaysAgo)) {
                groupName = "Last 30 Days";
            } else if (threadDate.isAfter(ninetyDaysAgo)) {
                groupName = "Last 90 Days";
            } else {
                groupName = "Older";
            }

            groupedThreads.computeIfAbsent(groupName, k -> new ArrayList<>()).add(summaryDTO);
        }

        // Convert to DTOs in the correct order, excluding empty groups
        List<ChatThreadGroupDTO> result = new ArrayList<>();
        String[] groupOrder = { "Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Last 90 Days", "Older" };

        for (String groupName : groupOrder) {
            List<ChatThreadSummaryDTO> groupThreads = groupedThreads.get(groupName);
            if (groupThreads != null && !groupThreads.isEmpty()) {
                result.add(new ChatThreadGroupDTO(groupName, groupThreads));
            }
        }

        log.debug("Found {} thread groups for user: {}", result.size(), user.getLogin());
        return result;
    }

    /**
     * Get a specific thread with full detail for a user.
     * Verifies that the thread belongs to the specified user and loads all messages.
     *
     * @param threadId the thread ID
     * @param user the authenticated user
     * @return Optional containing the thread detail if found and belongs to user
     */
    @Transactional(readOnly = true)
    public Optional<ChatThreadDetailDTO> getThreadDetailForUser(UUID threadId, User user) {
        log.debug("Getting thread detail {} for user: {}", threadId, user.getLogin());

        // Fetch thread with messages first
        Optional<ChatThread> threadOpt = chatThreadRepository.findByIdAndUserWithMessages(threadId, user);

        if (threadOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatThread thread = threadOpt.get();

        // Force lazy loading of message parts for all messages within transaction
        for (ChatMessage message : thread.getAllMessages()) {
            // Force initialization of the parts collection
            var parts = message.getParts();
            parts.size(); // Trigger lazy loading

            // Additionally, access each part to ensure they're fully loaded
            for (ChatMessagePart part : parts) {
                // Access part properties to ensure they're loaded
                part.getType();
                part.getContent();
                part.getOriginalType();
            }
        }

        return Optional.of(convertToDetailDTO(thread));
    }

    /**
     * Build the conversation path up to and including the specified message, ensuring the message belongs to the user.
     * This is used for message editing to reconstruct context based on previousMessageId rather than the selected leaf.
     */
    @Transactional(readOnly = true)
    public List<UIMessage> getConversationPathForMessage(UUID messageId, User user) {
        var messageOpt = chatMessageRepository.findById(messageId);
        if (messageOpt.isEmpty()) {
            return new ArrayList<>();
        }
        ChatMessage target = messageOpt.get();
        if (target.getThread() == null || target.getThread().getUser() == null || !user.getId().equals(target.getThread().getUser().getId())) {
            // Not owned by user or invalid thread association
            return new ArrayList<>();
        }

    // Use entity helper to get path from root to the target message
    List<ChatMessage> path = target.getPathFromRoot();

        // Convert to UIMessage list
        List<UIMessage> result = new ArrayList<>();
        for (ChatMessage m : path) {
            try {
                result.add(m.toUIMessage());
            } catch (Exception e) {
                log.error("Failed to convert message {} to UIMessage: {}", m.getId(), e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * Check if a thread exists and is owned by the user.
     *
     * @param threadId the thread ID
     * @param user the authenticated user
     * @return true if thread exists and is owned by user
     */
    public boolean isThreadOwnedByUser(UUID threadId, User user) {
        return chatThreadRepository.findByIdAndUser(threadId, user).isPresent();
    }

    /**
     * Convert a ChatThread to a summary DTO.
     */
    private ChatThreadSummaryDTO convertToSummaryDTO(ChatThread thread) {
        return new ChatThreadSummaryDTO(thread.getId(), thread.getTitle(), thread.getCreatedAt());
    }

    /**
     * Convert a ChatThread to a detail DTO with full message content.
     */
    private ChatThreadDetailDTO convertToDetailDTO(ChatThread thread) {
        // Get the conversation path from the selected leaf message
        List<UIMessage> conversationPath = getConversationPathAsUIMessages(thread);

        return new ChatThreadDetailDTO(
            thread.getId(),
            thread.getTitle(),
            thread.getCreatedAt(),
            conversationPath,
            thread.getSelectedLeafMessage() != null ? thread.getSelectedLeafMessage().getId() : null
        );
    }

    /**
     * Extract the conversation path from the thread's selected leaf message as UIMessage objects.
     * Traverses the parent chain to build the full conversation history.
     *
     * @param thread The thread with eagerly loaded messages
     * @return List of UIMessage objects representing the conversation path for frontend consumption
     */
    private List<UIMessage> getConversationPathAsUIMessages(ChatThread thread) {
        List<UIMessage> messages = new ArrayList<>();

        if (thread.getSelectedLeafMessage() == null) {
            log.debug("No selected leaf message for thread {}", thread.getId());
            return messages;
        }

        // Build the path by traversing from leaf to root
        ChatMessage currentMessage = thread.getSelectedLeafMessage();
        List<ChatMessage> path = new ArrayList<>();

        while (currentMessage != null) {
            path.add(0, currentMessage); // Add at beginning to maintain order
            currentMessage = currentMessage.getParentMessage();
        }

        // Convert to UIMessage objects
        for (ChatMessage message : path) {
            try {
                UIMessage uiMessage = message.toUIMessage();
                messages.add(uiMessage);
            } catch (Exception e) {
                log.error("Failed to convert message {} to UIMessage: {}", message.getId(), e.getMessage(), e);
                // Continue with other messages rather than failing completely
            }
        }

        log.debug("Built conversation path with {} messages for thread {}", messages.size(), thread.getId());
        return messages;
    }
}

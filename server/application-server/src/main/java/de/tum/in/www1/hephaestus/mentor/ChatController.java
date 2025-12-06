package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamErrorPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamFinishPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * REST controller for the chat functionality.
 * Handles streaming chat responses, message persistence, and thread management.
 */
@WorkspaceScopedController
@RequestMapping("/mentor")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final UserRepository userRepository;
    private final IntelligenceServiceWebClient intelligenceServiceWebClient;
    private final ChatPersistenceService chatPersistenceService;
    private final ChatThreadService chatThreadService;
    private final WorkspaceContextResolver workspaceResolver;

    @Hidden
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(WorkspaceContext workspaceContext, @RequestBody ChatRequestDTO chatRequest) {
        logger.info("Processing chat request in workspace {}", workspaceContext.slug());

        // Validate message size and content
        try {
            validateChatRequest(chatRequest);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid chat request: {}", e.getMessage());
            return Flux.error(
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    e.getMessage()
                )
            );
        }

        // Get current authenticated username
        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();

        // Find user by login in database
        var userOptional = userRepository.findByLogin(currentUserLogin);

        // TODO: Separate app user and GitHub user handling, this should not be needed
        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            return Flux.just(
                StreamPartProcessorUtils.streamPartToJson(
                    new StreamErrorPart().errorText("Sorry, we could not find your user.")
                ),
                StreamPartProcessorUtils.streamPartToJson(new StreamFinishPart()),
                StreamPartProcessorUtils.DONE_MARKER
            );
        }

        logger.debug("Forwarding chat request to intelligence service for user: {}", userOptional.get().getLogin());

        var user = userOptional.get();
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        // Build full context for the intelligence service from the thread ID BEFORE persisting the new message
        // We only receive the latest user message from the client now; reconstruct the prior path here
        List<UIMessage> fullMessages;
        boolean isValidThreadId = true;
        try {
            UUID threadUuid = UUID.fromString(chatRequest.id());
            Optional<ChatThreadDetailDTO> threadDetail = chatThreadService.getThreadDetailForUser(
                threadUuid,
                user,
                workspace
            );

            if (threadDetail.isPresent() && threadDetail.get().getMessages() != null) {
                // If editing (previousMessageId provided), build path up to that message; if null, start from scratch
                if (chatRequest.previousMessageId() != null) {
                    fullMessages = new ArrayList<>(
                        chatThreadService.getConversationPathForMessage(
                            chatRequest.previousMessageId(),
                            user,
                            workspace
                        )
                    );
                } else {
                    fullMessages = new ArrayList<>();
                }
            } else {
                fullMessages = new ArrayList<>();
            }

            // Append the new user message from the request
            if (chatRequest.message() != null) {
                fullMessages.add(chatRequest.message());
            }
        } catch (IllegalArgumentException e) {
            // If the ID is not a valid UUID (e.g., brand-new client-generated), fall back to request messages only
            logger.warn("Invalid thread UUID '{}', using request messages only: {}", chatRequest.id(), e.getMessage());
            fullMessages = chatRequest.message() != null ? List.of(chatRequest.message()) : List.of();
            isValidThreadId = false;
        }

        // Now that full context is captured, persist the new user message and prepare processor
        // If threadId was invalid, create a new request with a generated UUID for persistence
        ChatRequestDTO effectiveRequest = chatRequest;
        if (!isValidThreadId) {
            String newId = UUID.randomUUID().toString();
            effectiveRequest = new ChatRequestDTO(newId, chatRequest.message(), chatRequest.previousMessageId());
            logger.debug("Generated new thread ID {} for invalid request id '{}'", newId, chatRequest.id());
        }

        StreamPartProcessor processor = chatPersistenceService.createProcessorForRequest(
            user,
            effectiveRequest,
            workspace
        );

        ChatRequest intelligenceRequest = new ChatRequest();
        // Forward full message history as-is (including tool parts) to the intelligence service
        intelligenceRequest.setMessages(fullMessages);
        intelligenceRequest.setUserId(user.getId().intValue());

        // Debug: Log context being sent to intelligence service
        try {
            List<UIMessage> dbgList = fullMessages != null ? fullMessages : Collections.emptyList();
            int count = dbgList.size();
            String firstRole = count > 0 ? String.valueOf(dbgList.get(0).getRole()) : "none";
            String lastRole = count > 0 ? String.valueOf(dbgList.get(count - 1).getRole()) : "none";
            String firstText = "";
            String lastText = "";
            if (count > 0 && dbgList.get(0).getParts() != null) {
                var p = dbgList
                    .get(0)
                    .getParts()
                    .stream()
                    .filter(part -> "text".equals(part.getType()) && part.getText() != null)
                    .findFirst();
                firstText = p
                    .map(part -> part.getText().length() > 60 ? part.getText().substring(0, 60) + "…" : part.getText())
                    .orElse("");
            }
            if (count > 0 && dbgList.get(count - 1).getParts() != null) {
                var p2 = dbgList
                    .get(count - 1)
                    .getParts()
                    .stream()
                    .filter(part -> "text".equals(part.getType()) && part.getText() != null)
                    .findFirst();
                lastText = p2
                    .map(part -> part.getText().length() > 60 ? part.getText().substring(0, 60) + "…" : part.getText())
                    .orElse("");
            }
            logger.debug(
                "Intelligence request: messages={}, firstRole={}, lastRole={}, firstText='{}', lastText='{}'",
                count,
                firstRole,
                lastRole,
                firstText,
                lastText
            );
        } catch (Exception e) {
            logger.warn("Failed to log intelligence request context: {}", e.getMessage());
        }

        // Use the enhanced streaming with persistence callbacks
        return intelligenceServiceWebClient.streamChat(intelligenceRequest, processor);
    }

    /**
     * Get all chat threads for the authenticated user.
     * Returns thread summaries with basic information only.
     *
     * @return List of thread summaries ordered by creation date (newest first)
     */
    @Operation(
        summary = "Get user's chat threads",
        description = "Retrieve all chat threads for the authenticated user"
    )
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved threads"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
        }
    )
    @GetMapping("/threads")
    public ResponseEntity<List<ChatThreadSummaryDTO>> getThreads(WorkspaceContext workspaceContext) {
        logger.debug("Getting threads for authenticated user in workspace {}", workspaceContext.slug());

        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);

        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        var user = userOptional.get();
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        List<ChatThreadSummaryDTO> threads = chatThreadService.getThreadSummariesForUser(user, workspace);

        logger.debug("Retrieved {} threads for user: {}", threads.size(), user.getLogin());
        return ResponseEntity.ok(threads);
    }

    /**
     * Get all chat threads for the authenticated user grouped by time periods.
     * Returns thread summaries organized into groups: Today, Yesterday, Last 7 Days, Last 30 Days.
     *
     * @return List of thread groups ordered by time relevance
     */
    @Operation(
        summary = "Get user's grouped chat threads",
        description = "Retrieve all chat threads for the authenticated user grouped by time periods"
    )
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved grouped threads"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
        }
    )
    @GetMapping("/threads/grouped")
    public ResponseEntity<List<ChatThreadGroupDTO>> getGroupedThreads(WorkspaceContext workspaceContext) {
        logger.debug("Getting grouped threads for authenticated user in workspace {}", workspaceContext.slug());

        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);

        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        var user = userOptional.get();
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        List<ChatThreadGroupDTO> threadGroups = chatThreadService.getGroupedThreadSummariesForUser(user, workspace);

        logger.debug("Retrieved {} thread groups for user: {}", threadGroups.size(), user.getLogin());
        return ResponseEntity.ok(threadGroups);
    }

    /**
     * Get a specific chat thread with all its messages for the authenticated user.
     * Used to initialize useChat with existing conversation history.
     *
     * @param threadId The thread ID
     * @return Thread detail with full message content if found and owned by user
     */
    @Operation(summary = "Get chat thread detail", description = "Retrieve a specific chat thread with all messages")
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved thread"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "404", description = "Thread not found or not owned by user"),
        }
    )
    @GetMapping("/thread/{threadId}")
    public ResponseEntity<ChatThreadDetailDTO> getThread(
        WorkspaceContext workspaceContext,
        @Parameter(description = "Thread ID", required = true) @PathVariable UUID threadId
    ) {
        logger.debug("Getting thread detail for threadId: {} in workspace {}", threadId, workspaceContext.slug());

        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);

        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        var user = userOptional.get();
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        Optional<ChatThreadDetailDTO> threadDetail = chatThreadService.getThreadDetailForUser(
            threadId,
            user,
            workspace
        );

        if (threadDetail.isEmpty()) {
            logger.warn("Thread {} not found or not owned by user: {}", threadId, user.getLogin());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found or access denied");
        }

        logger.debug("Retrieved thread detail for threadId: {} and user: {}", threadId, user.getLogin());
        return ResponseEntity.ok(threadDetail.get());
    }

    private void validateChatRequest(ChatRequestDTO chatRequest) {
        var message = chatRequest.message();
        if (message == null) {
            throw new IllegalArgumentException("Chat request must contain a message");
        }

        if (message.getParts() == null || message.getParts().isEmpty()) {
            throw new IllegalArgumentException("Message must contain at least one part");
        }

        boolean hasNonEmptyText = false;
        for (var part : message.getParts()) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                String text = part.getText().trim();
                if (!text.isEmpty()) {
                    hasNonEmptyText = true;
                    if (text.length() > 20000) {
                        throw new IllegalArgumentException("Message too large - maximum 20,000 characters allowed");
                    }
                }
            }
        }

        if (!hasNonEmptyText) {
            throw new IllegalArgumentException("Message cannot be empty or contain only whitespace");
        }
    }
}

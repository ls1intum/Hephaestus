package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamErrorPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamFinishPart;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for the chat functionality.
 * Handles streaming chat responses, message persistence, and thread management.
 */
@RestController
@RequestMapping("/mentor")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IntelligenceServiceWebClient intelligenceServiceWebClient;

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    @Autowired
    private ChatThreadService chatThreadService;

    @Hidden
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO chatRequest) {
        logger.info("Processing chat request with {} messages", chatRequest.messages().size());

        // Validate message size and content
        try {
            validateChatRequest(chatRequest);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid chat request: {}", e.getMessage());
            return Flux.error(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage()));
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

        // Delegate to the persistence service to handle thread and message creation
        StreamPartProcessor processor = chatPersistenceService.createProcessorForRequest(user, chatRequest);

        ChatRequest intelligenceRequest = new ChatRequest();
        intelligenceRequest.setMessages(chatRequest.messages());
        intelligenceRequest.setUserId(user.getId().intValue());

        // Use the enhanced streaming with persistence callbacks
        return intelligenceServiceWebClient.streamChat(intelligenceRequest, processor);
    }

    /**
     * Get all chat threads for the authenticated user.
     * Returns thread summaries with basic information only.
     * 
     * @return List of thread summaries ordered by creation date (newest first)
     */
    @Operation(summary = "Get user's chat threads", description = "Retrieve all chat threads for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved threads"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/threads")
    public ResponseEntity<List<ChatThreadSummaryDTO>> getThreads() {
        logger.debug("Getting threads for authenticated user");
        
        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);
        
        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        
        var user = userOptional.get();
        List<ChatThreadSummaryDTO> threads = chatThreadService.getThreadSummariesForUser(user);
        
        logger.debug("Retrieved {} threads for user: {}", threads.size(), user.getLogin());
        return ResponseEntity.ok(threads);
    }

    /**
     * Get all chat threads for the authenticated user grouped by time periods.
     * Returns thread summaries organized into groups: Today, Yesterday, Last 7 Days, Last 30 Days.
     * 
     * @return List of thread groups ordered by time relevance
     */
    @Operation(summary = "Get user's grouped chat threads", description = "Retrieve all chat threads for the authenticated user grouped by time periods")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved grouped threads"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/threads/grouped")
    public ResponseEntity<List<ChatThreadGroupDTO>> getGroupedThreads() {
        logger.debug("Getting grouped threads for authenticated user");
        
        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);
        
        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        
        var user = userOptional.get();
        List<ChatThreadGroupDTO> threadGroups = chatThreadService.getGroupedThreadSummariesForUser(user);
        
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
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved thread"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "404", description = "Thread not found or not owned by user")
    })
    @GetMapping("/thread/{threadId}")
    public ResponseEntity<ChatThreadDetailDTO> getThread(
            @Parameter(description = "Thread ID", required = true) 
            @PathVariable UUID threadId) {
        logger.debug("Getting thread detail for threadId: {}", threadId);
        
        var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
        var userOptional = userRepository.findByLogin(currentUserLogin);
        
        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        
        var user = userOptional.get();
        Optional<ChatThreadDetailDTO> threadDetail = chatThreadService.getThreadDetailForUser(threadId, user);
        
        if (threadDetail.isEmpty()) {
            logger.warn("Thread {} not found or not owned by user: {}", threadId, user.getLogin());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found or access denied");
        }
        
        logger.debug("Retrieved thread detail for threadId: {} and user: {}", threadId, user.getLogin());
        return ResponseEntity.ok(threadDetail.get());
    }
    
    private void validateChatRequest(ChatRequestDTO chatRequest) {
        if (chatRequest.messages() == null || chatRequest.messages().isEmpty()) {
            throw new IllegalArgumentException("Chat request must contain at least one message");
        }
        
        // Check for empty or whitespace-only messages
        for (var message : chatRequest.messages()) {
            if (message.getParts() == null || message.getParts().isEmpty()) {
                throw new IllegalArgumentException("Message must contain at least one part");
            }
            
            boolean hasNonEmptyText = false;
            for (var part : message.getParts()) {
                if ("text".equals(part.getType()) && part.getText() != null) {
                    String text = part.getText().trim();
                    if (!text.isEmpty()) {
                        hasNonEmptyText = true;
                        // Check for very large messages (>20000 characters)
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
}

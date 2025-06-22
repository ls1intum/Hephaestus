package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamErrorPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamFinishPart;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for the chat functionality.
 * Handles streaming chat responses and message persistence.
 */
@RestController
@RequestMapping("/mentor/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IntelligenceServiceWebClient intelligenceServiceWebClient;

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    @Hidden
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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

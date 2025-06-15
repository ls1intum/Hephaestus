package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ChatService chatService;
    private final UserRepository userRepository;
    
    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }
    
    @Hidden
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO chatRequest) {
        logger.info("Processing chat request with {} messages", chatRequest.messages().size());
        logger.debug("Chat request details: id={}, first message content='{}'", 
                chatRequest.id(), 
                chatRequest.messages().isEmpty() ? "none" : 
                    chatRequest.messages().get(0).getContent());

        // Get current authenticated user
        var userOptional = userRepository.getCurrentUser();
        logger.debug("User authentication status: {}", userOptional.isPresent() ? "authenticated" : "not authenticated");
        
        if (userOptional.isEmpty()) {
            logger.warn("No authenticated user found for chat request");
            String errorResponse = "3:\"Authentication required\"\n";
            String finishResponse = "d:{\"finishReason\":\"error\"}\n";
            logger.debug("Returning authentication error response: {}", errorResponse + finishResponse);
            return Flux.just(errorResponse, finishResponse);
        }

        logger.debug("Forwarding chat request to ChatService for user: {}", userOptional.get().getLogin());
        return chatService.processChat(chatRequest, userOptional.get())
                .doOnNext(chunk -> logger.trace("Sending response chunk: {}", chunk))
                .doOnComplete(() -> logger.debug("Chat response completed"))
                .doOnError(e -> logger.error("Error during chat response streaming", e));
    }
}

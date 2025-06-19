package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
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
    private PersistenceStreamPartProcessor persistenceProcessor;

    @Hidden
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO chatRequest) {
        logger.info("Processing chat request with {} messages", chatRequest.messages().size());

        // Get current authenticated username
        var currentUserLogin = SecurityUtils.getCurrentUserLogin();
        
        if (currentUserLogin.isEmpty()) {
            logger.warn("No authenticated username found for chat request");
            return Flux.just(
                SseStreamParser.createErrorSSE("Authentication required"),
                SseStreamParser.createFinishSSE(),
                SseStreamParser.createDoneSSE()
            );
        }

        // Find user by login in database
        var userOptional = userRepository.findByLogin(currentUserLogin.get());

        if (userOptional.isEmpty()) {
            logger.warn("User not found for login: {}", currentUserLogin.get());
            return Flux.just(
                SseStreamParser.createErrorSSE("User not found"),
                SseStreamParser.createFinishSSE(),
                SseStreamParser.createDoneSSE()
            );
        }

        logger.debug("Forwarding chat request to intelligence service for user: {}", userOptional.get().getLogin());
        ChatRequest intelligenceRequest = new ChatRequest();
        intelligenceRequest.setMessages(chatRequest.messages());
        
        // Use the enhanced streaming with persistence callbacks
        return intelligenceServiceWebClient.streamChat(intelligenceRequest, persistenceProcessor);
    }
}

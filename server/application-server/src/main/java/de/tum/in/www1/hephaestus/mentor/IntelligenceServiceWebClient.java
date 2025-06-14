package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Web client for communicating with the Intelligence Service.
 * Handles streaming chat requests and responses.
 */
@Component
public interface IntelligenceServiceWebClient {
    
    /**
     * Send a chat request to the intelligence service and return streaming response.
     * 
     * @param request The chat request containing messages
     * @return Flux of streaming response chunks
     */
    Flux<String> streamChat(ChatRequest request);
}

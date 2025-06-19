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
     * Send a chat request with stream part processing callbacks.
     * This method allows for real-time processing of stream parts for persistence,
     * monitoring, and business logic while still returning the raw SSE stream.
     * 
     * @param request The chat request containing messages
     * @param processor The stream part processor for handling callbacks
     * @return Flux of streaming response chunks
     */
    Flux<String> streamChat(ChatRequest request, StreamPartProcessor processor);
}

package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

@RestController
@RequestMapping("/mentor")
public class MentorController {

    private static final Logger logger = LoggerFactory.getLogger(MentorController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final WebClient webClient;
    
    public MentorController(@Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(intelligenceServiceUrl)
            .build();
    }
    
    @Hidden // Hides it from the OpenAPI documentation
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody MentorChatRequestDTO mentorRequest) {
        logger.info("Received chat request with {} messages", mentorRequest.messages().size());
        //  Log messages for debugging
        mentorRequest.messages().forEach(message -> 
            logger.debug(message.toString())
        );

        try {
            // Convert to intelligence service ChatRequest and fix missing message IDs
            ChatRequest intelligenceRequest = new ChatRequest();
            intelligenceRequest.setMessages(mentorRequest.messages().stream()
                .map(message -> {
                    // Ensure each message has a unique ID
                    if (message.getId() == null || message.getId().isEmpty()) {
                        message.setId(UUID.randomUUID().toString());
                        logger.debug("Added missing ID {} to message", message.getId());
                    }
                    return message;
                })
                .toList());

            // Log the actual JSON payload being sent
            try {
                String jsonPayload = objectMapper.writeValueAsString(intelligenceRequest);
                logger.info("Sending JSON payload to intelligence service: {}", jsonPayload);
            } catch (Exception jsonException) {
                logger.error("Failed to serialize request to JSON for logging", jsonException);
            }

            return webClient.post()
                .uri("/mentor/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .bodyValue(intelligenceRequest)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response -> {
                    return response.bodyToMono(String.class)
                        .doOnNext(errorBody -> logger.error("422 Error response body: {}", errorBody))
                        .then(response.createException());
                })
                .bodyToFlux(String.class)
                .map(chunk -> {
                    // Ensure each chunk ends with proper line breaks for SSE
                    if (!chunk.endsWith("\n")) {
                        return chunk + "\n";
                    }
                    return chunk;
                })
                .doOnNext(chunk -> logger.debug("Streaming chunk: {}", chunk))
                .doOnError(error -> {
                    logger.error("Error calling intelligence service: {} - {}", 
                        error.getClass().getSimpleName(), error.getMessage());
                    if (error.getMessage() != null && error.getMessage().contains("422")) {
                        logger.error("Request payload that caused 422: {}", intelligenceRequest);
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Failed to call intelligence service", error);
                    // Fallback to simple error response
                    return Flux.just("3:\"Sorry, I encountered an error. Please try again.\"\n", 
                                   "d:{\"finishReason\":\"stop\"}\n");
                });
                
        } catch (Exception e) {
            logger.error("Failed to process chat request", e);
            // Fallback to simple error response
            return Flux.just("3:\"Sorry, I encountered an error. Please try again.\"\n", 
                           "d:{\"finishReason\":\"stop\"}\n");
        }
    }
}

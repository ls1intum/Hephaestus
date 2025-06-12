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

@RestController
@RequestMapping("/mentor")
public class MentorController {

    private static final Logger logger = LoggerFactory.getLogger(MentorController.class);
    
    private final WebClient webClient;
    
    public MentorController(@Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(intelligenceServiceUrl)
            .build();
    }
    
    @Hidden // Hides it from the OpenAPI documentation
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request with {} messages", request.getMessages().size());

        try {
            // The request is already the perfect type! Just pass it directly to the intelligence service
            return webClient.post()
                .uri("/mentor/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .bodyValue(request)
                .retrieve()
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
                        logger.error("Request payload that caused 422: {}", request);
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

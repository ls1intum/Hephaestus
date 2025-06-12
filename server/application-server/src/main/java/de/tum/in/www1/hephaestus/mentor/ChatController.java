package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.UUID;

@RestController
@RequestMapping("/mentor/chat")
public class ChatController {

    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final WebClient webClient;
    
    public ChatController(@Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl) {
        // Configure ObjectMapper to properly handle JsonNullable fields
        this.objectMapper = new ObjectMapper()
            .registerModule(new JsonNullableModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            
        // Configure WebClient with custom ObjectMapper for JsonNullable support
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
            })
            .build();
            
        this.webClient = WebClient.builder()
            .baseUrl(intelligenceServiceUrl)
            .exchangeStrategies(strategies)
            .build();
    }
    
    @Hidden
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO mentorRequest) {
        logger.info("Processing chat request with {} messages", mentorRequest.messages().size());

        ChatRequest intelligenceRequest = new ChatRequest();
        intelligenceRequest.setMessages(mentorRequest.messages().stream()
            .map(message -> {
                if (message.getId() == null || message.getId().isEmpty()) {
                    message.setId(UUID.randomUUID().toString());
                }
                return message;
            })
            .toList());

        return webClient.post()
            .uri("/mentor/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_PLAIN)
            .bodyValue(intelligenceRequest)
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> chunk.endsWith("\n") ? chunk : chunk + "\n")
            .onErrorResume(error -> {
                logger.error("Failed to call intelligence service", error);
                return Flux.just(
                    "3:\"Sorry, I encountered an error. Please try again.\"\n", 
                    "d:{\"finishReason\":\"stop\"}\n"
                );
            });
    }
}

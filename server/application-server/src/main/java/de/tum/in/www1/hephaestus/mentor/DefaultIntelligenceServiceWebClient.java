package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Default implementation of IntelligenceServiceWebClient using WebClient.
 */
@Component
@Profile("!test") // Exclude this implementation in test profile
public class DefaultIntelligenceServiceWebClient implements IntelligenceServiceWebClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultIntelligenceServiceWebClient.class);
    private final WebClient webClient;
    
    public DefaultIntelligenceServiceWebClient(@Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl) {
        // Configure ObjectMapper to properly handle JsonNullable fields
        var objectMapper = new ObjectMapper()
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
        logger.info("Configured Intelligence Service WebClient with URL: {}", intelligenceServiceUrl);
    }
    
    @Override
    public Flux<String> streamChat(ChatRequest request) {
        logger.debug("Sending chat request to intelligence service");
        
        return webClient.post()
            .uri("/mentor/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_PLAIN)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class)
            .map(chunk -> chunk + "\n")
            .onErrorResume(error -> {
                logger.error("Failed to call intelligence service", error);
                return Flux.just(
                    "3:\"Sorry, I encountered an error. Please try again.\"\n", 
                    "d:{\"finishReason\":\"stop\"}\n"
                );
            });
    }
}

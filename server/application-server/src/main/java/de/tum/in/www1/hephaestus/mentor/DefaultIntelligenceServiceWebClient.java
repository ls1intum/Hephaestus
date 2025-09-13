package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamErrorPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamFinishPart;
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

    public DefaultIntelligenceServiceWebClient(
        @Value("${hephaestus.intelligence-service.url}") String intelligenceServiceUrl
    ) {
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

        this.webClient = WebClient.builder().baseUrl(intelligenceServiceUrl).exchangeStrategies(strategies).build();
        logger.info("Configured Intelligence Service WebClient with URL: {}", intelligenceServiceUrl);
    }

    @Override
    public Flux<String> streamChat(ChatRequest request, StreamPartProcessor processor) {
        logger.debug(
            "Sending chat request to intelligence service with {} messages",
            request.getMessages() != null ? request.getMessages().size() : 0
        );

        return webClient
            .post()
            .uri("/mentor/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class)
            .doOnSubscribe(s -> logger.debug("Subscribed to intelligence service SSE stream"))
            .doOnNext(chunk -> logger.trace("Received SSE chunk: {}", chunk))
            // Parse and process stream parts with callbacks
            .doOnNext(chunk -> StreamPartProcessorUtils.processStreamChunk(chunk, processor))
            .doOnComplete(() -> logger.debug("Intelligence service SSE stream completed"))
            .doOnError(error -> logger.error("Failed to call intelligence service", error))
            .onErrorResume(error -> {
                logger.error("Error in intelligence service call, returning fallback SSE response", error);
                return Flux.just(
                    StreamPartProcessorUtils.streamPartToJson(
                        new StreamErrorPart().errorText("Sorry, I encountered an error. Please try again.")
                    ),
                    StreamPartProcessorUtils.streamPartToJson(new StreamFinishPart()),
                    StreamPartProcessorUtils.DONE_MARKER
                );
            });
    }
}

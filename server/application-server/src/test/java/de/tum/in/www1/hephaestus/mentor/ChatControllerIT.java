package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private StubChatFrameHolder stubFrameHolder;

    @BeforeEach
    void setUp() {
        // Set default frames before each test
        
    }

    @DynamicPropertySource
    static void configureStubUrlWithPort(DynamicPropertyRegistry registry) {
        // Use a different approach: we'll override this in a non-static way later
        // For now, this placeholder will be replaced
        registry.add("hephaestus.intelligence-service.url", () -> "PLACEHOLDER_WILL_BE_REPLACED");
    }

    /**
     * Verify that the stub controller returns the frames correctly.
     * This is more of a sanity check to ensure the stub is working as expected.
     */
    @Test
    void stubControllerReturnsCorrectFrames() {
        // Arrange
        ChatRequest request = new ChatRequest();

        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setRole("user");
        message.addPartsItem(new MessagePartsInner().type("text").text("Hello"));
        request.setMessages(List.of(message));
        stubFrameHolder.frames = List.of(
            "0:\"Hello world\"",
            "0:\"!\"",
            "d:{\"finishReason\":\"stop\"}"
        );

        // Act
        Flux<String> stubStreamed = webTestClient.post()
            .uri("/_stub/intelligence-service/mentor/chat")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();

        // Assert
        StepVerifier.create(stubStreamed)
            .expectNext("0:\"Hello world\"")
            .expectNext("0:\"!\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }
}

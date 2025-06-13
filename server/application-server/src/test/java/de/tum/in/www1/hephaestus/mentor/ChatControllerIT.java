package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    private MockIntelligenceService mockIntelligenceService;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        mockIntelligenceService = MockIntelligenceService.create();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockIntelligenceService != null) {
            mockIntelligenceService.dispose();
        }
    }
    
    @Test
    @DisplayName("Should stream AI response chunks correctly")
    void shouldStreamAiResponseChunksCorrectly() {
        // Given a request with messages
        ChatRequestDTO request = new ChatRequestDTO(
            UUID.randomUUID().toString(),
            createSampleMessages()
        );
        
        // And a mock response with specific chunks
        List<String> responseFrames = List.of(
            "0:\"Hello, I'm your coding assistant.\"\n",
            "0:\" How can I help you today?\"\n",
            "d:{\"finishReason\":\"stop\"}\n"
        );
        mockIntelligenceService.responseWith(responseFrames);
        
        // When the chat endpoint is called
        Flux<String> responseBody = webTestClient.post()
            .uri("/mentor/chat")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();
        
        // Then the response should stream each frame correctly
        StepVerifier.create(responseBody)
            .expectNext("0:\"Hello, I'm your coding assistant.\"\n")
            .expectNext("0:\" How can I help you today?\"\n")
            .expectNext("d:{\"finishReason\":\"stop\"}\n")
            .verifyComplete();
    }
    
    private List<Message> createSampleMessages() {
        List<Message> messages = new ArrayList<>();
        
        Message userMessage = new Message();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setContent("Can you help me with my Java project?");
        userMessage.setRole("user");
        
        messages.add(userMessage);
        return messages;
    }
}

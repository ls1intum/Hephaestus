package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
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
    private MockChatFrameHolder mockFrameHolder;

    @BeforeEach
    void setUp() {
        mockFrameHolder.frames = List.of();
    }

    @Test
    void shouldStreamChatFramesCorrectly() {
        // Given
        var expectedFrames = List.of(
            "0:\"Hello world\"",
            "0:\"!\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("0:\"Hello world\"")
            .expectNext("0:\"!\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    private Flux<String> performChatRequest(List<String> mockFrames) {
        mockFrameHolder.frames = mockFrames;
        
        return webTestClient.post()
            .uri("/mentor/chat")
            .bodyValue(createChatRequest())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("text/plain")
            .returnResult(String.class)
            .getResponseBody();
    }

    private ChatRequestDTO createChatRequest() {
        return new ChatRequestDTO(
            UUID.randomUUID().toString(),
            List.of(createMessage("user", "Test message"))
        );
    }

    private Message createMessage(String role, String content) {
        var message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.addPartsItem(new MessagePartsInner().type("text").text(content));
        return message;
    }
}

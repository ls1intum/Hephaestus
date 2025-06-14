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
    void shouldStreamBasicTextResponse() {
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

    @Test
    void shouldStreamTextAndReasoningParts() {
        // Given - simulates AI thinking and responding
        var expectedFrames = List.of(
            "g:\"I need to analyze this question carefully\"",
            "0:\"Based on my analysis, \"",
            "0:\"the answer is 42\"",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":15}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("g:\"I need to analyze this question carefully\"")
            .expectNext("0:\"Based on my analysis, \"")
            .expectNext("0:\"the answer is 42\"")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":10,\"completionTokens\":15}}")
            .verifyComplete();
    }

    @Test
    void shouldStreamToolCallSequence() {
        // Given - simulates complete tool call workflow
        var expectedFrames = List.of(
            "0:\"I'll help you with that calculation. \"",
            "b:{\"toolCallId\":\"call-123\",\"toolName\":\"calculator\"}",
            "c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"{\"}",
            "c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"operation\\\":\"}",
            "c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"multiply\\\",\"}",
            "c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"a\\\":5,\\\"b\\\":3}\"}",
            "9:{\"toolCallId\":\"call-123\",\"toolName\":\"calculator\",\"args\":{\"operation\":\"multiply\",\"a\":5,\"b\":3}}",
            "a:{\"toolCallId\":\"call-123\",\"result\":15}",
            "0:\"The result is 15.\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("0:\"I'll help you with that calculation. \"")
            .expectNext("b:{\"toolCallId\":\"call-123\",\"toolName\":\"calculator\"}")
            .expectNext("c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"{\"}")
            .expectNext("c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"operation\\\":\"}")
            .expectNext("c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"multiply\\\",\"}")
            .expectNext("c:{\"toolCallId\":\"call-123\",\"argsTextDelta\":\"\\\"a\\\":5,\\\"b\\\":3}\"}")
            .expectNext("9:{\"toolCallId\":\"call-123\",\"toolName\":\"calculator\",\"args\":{\"operation\":\"multiply\",\"a\":5,\"b\":3}}")
            .expectNext("a:{\"toolCallId\":\"call-123\",\"result\":15}")
            .expectNext("0:\"The result is 15.\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    @Test
    void shouldStreamMultiStepResponse() {
        // Given - simulates multi-step reasoning with step boundaries
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-1\"}",
            "0:\"Let me think about this step by step.\"",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"promptTokens\":5,\"completionTokens\":10},\"isContinued\":true}",
            "f:{\"messageId\":\"msg-2\"}",
            "0:\"Now I'll provide the final answer: \"",
            "0:\"The solution is X.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":8,\"completionTokens\":12},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":13,\"completionTokens\":22}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-1\"}")
            .expectNext("0:\"Let me think about this step by step.\"")
            .expectNext("e:{\"finishReason\":\"tool-calls\",\"usage\":{\"promptTokens\":5,\"completionTokens\":10},\"isContinued\":true}")
            .expectNext("f:{\"messageId\":\"msg-2\"}")
            .expectNext("0:\"Now I'll provide the final answer: \"")
            .expectNext("0:\"The solution is X.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":8,\"completionTokens\":12},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":13,\"completionTokens\":22}}")
            .verifyComplete();
    }

    @Test
    void shouldStreamSourceAndFileAttachments() {
        // Given - simulates response with source citations and file attachments
        var expectedFrames = List.of(
            "0:\"According to the documentation, \"",
            "h:{\"sourceType\":\"url\",\"id\":\"source-1\",\"url\":\"https://example.com/docs\",\"title\":\"API Documentation\"}",
            "0:\"here's the relevant information. \"",
            "k:{\"data\":\"aGVsbG8gd29ybGQ=\",\"mimeType\":\"text/plain\"}",
            "0:\"Please see the attached file for details.\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("0:\"According to the documentation, \"")
            .expectNext("h:{\"sourceType\":\"url\",\"id\":\"source-1\",\"url\":\"https://example.com/docs\",\"title\":\"API Documentation\"}")
            .expectNext("0:\"here's the relevant information. \"")
            .expectNext("k:{\"data\":\"aGVsbG8gd29ybGQ=\",\"mimeType\":\"text/plain\"}")
            .expectNext("0:\"Please see the attached file for details.\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    @Test
    void shouldStreamErrorRecovery() {
        // Given - simulates error handling during streaming
        var expectedFrames = List.of(
            "0:\"I'll help you with that. \"",
            "3:\"Rate limit exceeded. Retrying...\"",
            "0:\"Sorry for the delay. \"",
            "0:\"Here's your answer.\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("0:\"I'll help you with that. \"")
            .expectNext("3:\"Rate limit exceeded. Retrying...\"")
            .expectNext("0:\"Sorry for the delay. \"")
            .expectNext("0:\"Here's your answer.\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    @Test
    void shouldStreamDataAndAnnotations() {
        // Given - simulates structured data and annotations
        var expectedFrames = List.of(
            "0:\"Here's the analysis result:\"",
            "2:[{\"metric\":\"accuracy\",\"value\":0.95},{\"metric\":\"precision\",\"value\":0.87}]",
            "8:[{\"id\":\"analysis-123\",\"timestamp\":\"2025-06-14T10:30:00Z\",\"confidence\":\"high\"}]",
            "0:\"The model performed well with 95% accuracy.\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("0:\"Here's the analysis result:\"")
            .expectNext("2:[{\"metric\":\"accuracy\",\"value\":0.95},{\"metric\":\"precision\",\"value\":0.87}]")
            .expectNext("8:[{\"id\":\"analysis-123\",\"timestamp\":\"2025-06-14T10:30:00Z\",\"confidence\":\"high\"}]")
            .expectNext("0:\"The model performed well with 95% accuracy.\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    @Test
    void shouldStreamRedactedReasoningWithSignature() {
        // Given - simulates sensitive reasoning that gets redacted
        var expectedFrames = List.of(
            "g:\"I need to analyze this sensitive data\"",
            "i:{\"data\":\"This reasoning has been redacted for security purposes.\"}",
            "j:{\"signature\":\"abc123xyz789\"}",
            "0:\"Based on the available information, \"",
            "0:\"I can provide this general guidance.\"",
            "d:{\"finishReason\":\"stop\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("g:\"I need to analyze this sensitive data\"")
            .expectNext("i:{\"data\":\"This reasoning has been redacted for security purposes.\"}")
            .expectNext("j:{\"signature\":\"abc123xyz789\"}")
            .expectNext("0:\"Based on the available information, \"")
            .expectNext("0:\"I can provide this general guidance.\"")
            .expectNext("d:{\"finishReason\":\"stop\"}")
            .verifyComplete();
    }

    @Test
    void shouldHandleEmptyResponse() {
        // Given - simulates immediate completion without content
        var expectedFrames = List.of(
            "d:{\"finishReason\":\"content-filter\"}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("d:{\"finishReason\":\"content-filter\"}")
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

    private ChatRequestDTO createMultiMessageRequest() {
        return new ChatRequestDTO(
            UUID.randomUUID().toString(),
            List.of(
                createMessage("user", "What is quantum computing?"),
                createAssistantMessage("Quantum computing is a type of computation that uses quantum mechanics..."),
                createMessage("user", "Can you show me an example calculation?")
            )
        );
    }

    private Message createMessage(String role, String content) {
        var message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.addPartsItem(new MessagePartsInner().type("text").text(content));
        return message;
    }

    private Message createAssistantMessage(String content) {
        return createMessage("assistant", content);
    }

    private Message createMultiPartMessage(String role) {
        var message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.addPartsItem(new MessagePartsInner().type("text").text("Here's some text"));
        message.addPartsItem(new MessagePartsInner().type("reasoning").reasoning("I need to think about this"));
        return message;
    }
}

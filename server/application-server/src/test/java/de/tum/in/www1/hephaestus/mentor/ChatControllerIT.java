package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MockChatFrameHolder mockFrameHolder;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    @WithMentorUser
    void shouldHandleCompleteTextMessageProtocol() {
        // Given
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        final List<String> responseFrames = List.of(
            "f:{\"messageId\":\"" + responseMessageId + "\"}",  // Start step with message ID
            "0:\"Hello, \"",                                 // Text part 1
            "0:\"this is a \"",                              // Text part 2
            "0:\"complete AI SDK test!\"",                   // Text part 3
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12}}"
        );

        // When - Process chat request (streaming)
        var response = performChatRequestWithFrames(request, responseFrames);

        // Then - Verify streaming protocol compliance
        StepVerifier.create(response)
            .expectNext("f:{\"messageId\":\"" + responseMessageId + "\"}")
            .expectNext("0:\"Hello, \"")
            .expectNext("0:\"this is a \"")
            .expectNext("0:\"complete AI SDK test!\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12}}")
            .verifyComplete();
        
        // And - Verify persistence

        // Thread should have been created with the request ID
        ChatThread thread = chatThreadRepository.findById(UUID.fromString(request.id()))
            .orElseThrow(() -> new AssertionError("No chat thread found"));
        assertThat(thread.getUser()).isNotNull(); // Thread should have a user

        // User message should have been created with the request ID
        ChatMessage userMessage = chatMessageRepository.findById(UUID.fromString(request.messages().getLast().getId()))
            .orElseThrow(() -> new AssertionError("No user message found"));
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getId().toString()).isEqualTo(responseMessageId);
        assertThat(userMessage.getParts()).hasSize(1); // Only one part for the user message
        ChatMessagePart userPart = userMessage.getParts().get(0);
        assertThat(userPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(userPart.getContent().asText()).isEqualTo("Hello, World!");

        // Assistant message should have been created with the response ID
        ChatMessage assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId))
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        assertThat(assistantMessage.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistantMessage.getParts()).hasSize(1); // Only one part for the assistant message
        ChatMessagePart assistantPart = assistantMessage.getParts().get(0);
        assertThat(assistantPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(assistantPart.getContent().asText()).isEqualTo("Hello, this is a complete AI SDK test!");

        // Relationships should be established
        assertThat(assistantMessage.getThread()).isEqualTo(thread);
        assertThat(userMessage.getThread()).isEqualTo(thread);
        assertThat(userMessage.getParentMessage()).isNull(); // Root message has no parent
        assertThat(assistantMessage.getParentMessage()).isEqualTo(userMessage);
        assertThat(thread.getAllMessages()).containsExactly(userMessage, assistantMessage);
        assertThat(thread.getSelectedLeafMessage()).isEqualTo(assistantMessage);
    }

    @Test
    @WithMentorUser
    void shouldHandleReasoning() {
        // Given
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        final List<String> responseFrames = List.of(
            "f:{\"messageId\":\"" + responseMessageId + "\"}",
            "g:\"I will open the conversation\"",
            "g:\" with witty banter. \"",
            "g:\"Once the user has relaxed,\"",
            "g:\" I will pry for valuable information.\"",
            "0:\"Hello, \"",
            "0:\"what's up?\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12}}"
        );

        // When - Process chat request
        performChatRequestWithFrames(request, responseFrames);

        // Then - Verify persistence of reasoning parts
        ChatMessage assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId))
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        assertThat(assistantMessage.getParts()).hasSize(2); // 1 reasoning part + 1 text part
        ChatMessagePart reasoningPart = assistantMessage.getParts().get(0);
        assertThat(reasoningPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.REASONING);
        assertThat(reasoningPart.getContent().asText()).contains("I will open the conversation with witty banter. Once the user has relaxed, I will pry for valuable information.");
        ChatMessagePart textPart = assistantMessage.getParts().get(1);
        assertThat(textPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart.getContent().asText()).isEqualTo("Hello, what's up?");
    }

    @Test
    @WithMentorUser
    void shouldHandleToolCallWithMultipleResponseMessages() {
        // Given
        var request = createChatRequest();
        String responseMessageId1 = UUID.randomUUID().toString();
        String responseMessageId2 = UUID.randomUUID().toString();

        final List<String> responseFrames = List.of(
            "f:{\"messageId\":\"" + responseMessageId1 + "\"}",
            "0:\"I'm retrieving the \"",
            "0:\"weather for Munich...\"",
            "9:{\"toolCallId\":\"call_37478204\",\"toolName\":\"getWeather\",\"args\":{\"latitude\":48.1475,\"longitude\":11.5865}}\n",
            "a:{\"toolCallId\":\"call_37478204\",\"result\": \"25.8°C\"}",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"promptTokens\":1004,\"completionTokens\":66},\"isContinued\":false}",
            "f:{\"messageId\":\"" + responseMessageId2 + "\"}",
            "0:\"The current weather in Munich is \"",
            "0:\"25.8°C.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":5259,\"completionTokens\":28},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"promptTokens\":6263,\"completionTokens\":94}}"
        );

        // When - Process chat request
        performChatRequestWithFrames(request, responseFrames);

        // Then - Verify persistence of tool call and text parts in one message
        assertThat(chatMessageRepository.findById(UUID.fromString(responseMessageId1)).isPresent()).isFalse(); 
        // First response message should not exist as it should be combined with the second one -> one assistant message

        ChatMessage combinedMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId2))
            .orElseThrow(() -> new AssertionError("No second response message found"));
        assertThat(combinedMessage.getParts()).hasSize(3); // 2 text parts + 1 tool invocation part
        ChatMessagePart textPart1 = combinedMessage.getParts().get(0);
        assertThat(textPart1.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart1.getContent().asText()).isEqualTo("I'm retrieving the weather for Munich...");

        ChatMessagePart toolInvocationPart = combinedMessage.getParts().get(1);
        assertThat(toolInvocationPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.TOOL_INVOCATION);
        JsonNode toolContent = toolInvocationPart.getContent();
        assertThat(toolContent.get("toolCallId").asText()).isEqualTo("call_37478204");
        assertThat(toolContent.get("toolName").asText()).isEqualTo("getWeather");
        assertThat(toolContent.get("result").asText()).isEqualTo("25.8°C");

        ChatMessagePart textPart2 = combinedMessage.getParts().get(2);
        assertThat(textPart2.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart2.getContent().asText()).isEqualTo("The current weather in Munich is 25.8°C.");
    }

    private ChatRequestDTO createChatRequest() {
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var message = new Message();
        message.setId(requestMessageId.toString());
        message.setRole("user");
        message.addPartsItem(new MessagePartsInner().type("text").text("Hello, World!"));

        return new ChatRequestDTO(
            requestThreadId.toString(),
            List.of(message)
        );
    }

    private Flux<String> performChatRequestWithFrames(ChatRequestDTO request, List<String> mockFrames) {
        mockFrameHolder.setFrames(request.id().toString(), mockFrames);

        return webTestClient.post()
            .uri("/mentor/chat")
            .headers(this::addAuthorizationHeader)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("text/plain")
            .returnResult(String.class)
            .getResponseBody();
    }

    private void addAuthorizationHeader(HttpHeaders headers) {
        // Add a mock JWT token that will be processed by our mock JwtDecoder
        headers.setBearerAuth("mock-jwt-token-for-mentor-user");
    }
}

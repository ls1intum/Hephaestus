package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MockChatResponseHolder mockResponseHolder;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Helper method to create text streaming parts in AI SDK v5 format.
     * @param textSegments Array of text segments to stream
     * @return List of streaming parts (start, deltas, end)
     */
    private List<Object> createTextStreamParts(String... textSegments) {
        String textId = UUID.randomUUID().toString();
        List<Object> parts = new ArrayList<>();

        parts.add(new StreamTextStartPart().id(textId));
        for (String segment : textSegments) {
            parts.add(new StreamTextDeltaPart().id(textId).delta(segment));
        }
        parts.add(new StreamTextEndPart().id(textId));

        return parts;
    }

    /**
     * Helper method to create reasoning streaming parts in AI SDK v5 format.
     * @param reasoningSegments Array of reasoning segments to stream
     * @return List of streaming parts (start, deltas, end)
     */
    private List<Object> createReasoningStreamParts(String... reasoningSegments) {
        String reasoningId = UUID.randomUUID().toString();
        List<Object> parts = new ArrayList<>();

        parts.add(new StreamReasoningStartPart().id(reasoningId));
        for (String segment : reasoningSegments) {
            parts.add(new StreamReasoningDeltaPart().id(reasoningId).delta(segment));
        }
        parts.add(new StreamReasoningEndPart().id(reasoningId));

        return parts;
    }

    /**
     * Helper method to extract text content from a part that uses structured content format.
     * For text parts, this will extract the "text" field from the structured JSON.
     * For reasoning parts, this will extract the reasoning content appropriately.
     * For backward compatibility during the test migration period.
     */
    private String extractTextContent(ChatMessagePart part) {
        if (part.getType() == ChatMessagePart.PartType.TEXT || part.getType() == ChatMessagePart.PartType.REASONING) {
            var uiPart = part.toUIMessagePart();
            return uiPart.getText();
        } else {
            // For non-text parts, fallback to raw content
            return part.getContent().asText();
        }
    }

    @Test
    void testUnauthenticatedRequestShouldBeRejected() {
        // Given
        var request = createChatRequest();

        // When & Then - unauthenticated request should be rejected
        webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUserOrNone()) // No auth header
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isUnauthorized(); // Expect 401 Unauthorized
    }

    @Test
    @WithMentorUser
    void testBasicUserAssistantTextConversationIsPersisted() {
        // Given: A new chat with a single user text message
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String textId = UUID.randomUUID().toString();

        mockResponseHolder.setStreamParts(
            request.message().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextStartPart().id(textId),
                new StreamTextDeltaPart().id(textId).delta("Hello,"),
                new StreamTextDeltaPart().id(textId).delta(" this"),
                new StreamTextDeltaPart().id(textId).delta(" is"),
                new StreamTextDeltaPart().id(textId).delta(" a"),
                new StreamTextDeltaPart().id(textId).delta(" test!"),
                new StreamTextEndPart().id(textId),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

        // When: The AI responds with a simple text response
        var response = performChatRequest(request);

        // Then: The thread contains both messages in the correct order with proper parent-child relationships
        StepVerifier.create(response).expectComplete();

        // Thread should have been created with the request ID
        ChatThread thread = chatThreadRepository
            .findById(UUID.fromString(request.id()))
            .orElseThrow(() -> new AssertionError("No chat thread found"));
        assertThat(thread.getUser().getLogin()).isEqualTo("mentor");
        assertThat(thread.getTitle()).isNotEmpty(); // Title should be a formatted date/time, not empty

        // User message should have been created with the request ID
        ChatMessage userMessage = chatMessageRepository
            .findById(UUID.fromString(request.message().getId()))
            .orElseThrow(() -> new AssertionError("No user message from request found"));
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts()).hasSize(1); // Only one part for the user message

        ChatMessagePart userPart = userMessage.getParts().get(0);
        assertThat(userPart.getType()).isEqualTo(ChatMessagePart.PartType.TEXT);

        // With our new structured content format, extract text from the structured content
        var userPartContent = userPart.toUIMessagePart();
        assertThat(userPartContent.getText()).isEqualTo("Hello, World!");

        // Assistant message should have been created with the response ID
        ChatMessage assistantMessage = chatMessageRepository
            .findById(UUID.fromString(responseMessageId))
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        assertThat(assistantMessage.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);

        // Verify we have the expected parts per AI SDK structure: step-start + text
        assertThat(assistantMessage.getParts()).hasSize(2);
        var parts = assistantMessage.getParts();

        // First part should be step-start (AI SDK expects these to be persisted)
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Second part should be the accumulated text content
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);

        // With our new structured content format, extract text from the structured content
        var assistantTextPart = parts.get(1).toUIMessagePart();
        assertThat(assistantTextPart.getText()).isEqualTo("Hello, this is a test!");

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
    void testContinueExistingChatThreadWithNewMessages() {
        // Given: An existing chat thread with previous messages
        var existingThread = new ChatThread();
        existingThread.setId(UUID.randomUUID());
        existingThread.setUser(createTestUser());
        existingThread.setTitle("Existing chat");
        existingThread = chatThreadRepository.save(existingThread);

        var existingUserMessage = createMessageInThread(existingThread, ChatMessage.Role.USER, "What is 1+1?", null);
        var existingAssistantMessage = createMessageInThread(
            existingThread,
            ChatMessage.Role.ASSISTANT,
            "1+1 is 2",
            existingUserMessage
        );

        existingThread.setSelectedLeafMessage(existingAssistantMessage);
        chatThreadRepository.save(existingThread);

        String responseMessageId = UUID.randomUUID().toString();

        var followUpMessage = new UIMessage();
        followUpMessage.setId(UUID.randomUUID().toString());
        followUpMessage.setRole(UIMessage.RoleEnum.USER);
        followUpMessage.addPartsItem(new UIMessagePartsInner().type("text").text("Follow-up question, what is 1+2?"));

        var request = new ChatRequestDTO(
            existingThread.getId().toString(),
            followUpMessage,
            existingAssistantMessage.getId()
        );

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(
            createTextStreamParts(
                "Thank you for the follow-up. ",
                "This builds on our previous conversation. ",
                "Now, 1+2 is 3."
            )
        );
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(followUpMessage.getId().toString(), streamParts);

        // When: A new user message is sent and AI responds
        var response = performChatRequest(request);

        // Then: New messages are added to the existing thread with correct linking
        StepVerifier.create(response).expectComplete();

        var refreshedThread = chatThreadRepository.findById(existingThread.getId()).orElseThrow();
        assertThat(refreshedThread.getAllMessages()).hasSize(4); // 2 existing + 2 new

        var newUserMessage = chatMessageRepository.findById(UUID.fromString(followUpMessage.getId())).orElseThrow();
        assertThat(newUserMessage.getThread()).isEqualTo(refreshedThread);
        assertThat(newUserMessage.getParentMessage().getId()).isEqualTo(existingAssistantMessage.getId());
        assertThat(newUserMessage.getParts()).hasSize(1);

        // With our new structured content format, extract text from the structured content
        var newUserPartContent = newUserMessage.getParts().get(0).toUIMessagePart();
        assertThat(newUserPartContent.getText()).isEqualTo("Follow-up question, what is 1+2?");

        var newAssistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        assertThat(newAssistantMessage.getThread()).isEqualTo(refreshedThread);
        assertThat(newAssistantMessage.getParentMessage().getId()).isEqualTo(newUserMessage.getId());
        assertThat(newAssistantMessage.getParts()).hasSize(2); // step-start + text
        assertThat(newAssistantMessage.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(newAssistantMessage.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);

        // With our new structured content format, extract text from the structured content
        var newAssistantPartContent = newAssistantMessage.getParts().get(1).toUIMessagePart();
        assertThat(newAssistantPartContent.getText()).isEqualTo(
            "Thank you for the follow-up. This builds on our previous conversation. Now, 1+2 is 3."
        );

        assertThat(refreshedThread.getSelectedLeafMessage()).isEqualTo(newAssistantMessage);
    }

    @Test
    @WithMentorUser
    void testReasoningPartPersistence() {
        // Given: A chat with a user question requiring explanation
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(
            createReasoningStreamParts(
                "Let me think about this step by step. ",
                "First, I need to analyze the problem. ",
                "Then I'll provide a solution."
            )
        );
        streamParts.addAll(createTextStreamParts("Based on my analysis, ", "here's the answer."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with reasoning parts along with text
        var response = performChatRequest(request);

        // Then: Both reasoning and text parts are persisted with proper types and ordering
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Verify message has exactly 3 parts (step-start + reasoning + text)
        assertThat(parts).hasSize(3);

        // Verify step-start part
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify reasoning part
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.REASONING);
        assertThat(extractTextContent(parts.get(1))).isEqualTo(
            "Let me think about this step by step. First, I need to analyze the problem. Then I'll provide a solution."
        );

        // Verify text part
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(2))).isEqualTo("Based on my analysis, here's the answer.");
    }

    @Test
    @WithMentorUser
    void testToolCallAndToolResultPersistence() {
        // Given: A user message requesting weather information
        var request = createChatRequest();
        String responseMessageId = "0ea53ae4-a149-4428-8d56-b76a872b4678";
        String toolCallId = "call_dGexcNCb8AVioJpEN7U8L8KE";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Let me check the current weather ", "for your location in Munich."));

        // Tool call - Weather API - Input
        streamParts.add(new StreamToolInputStartPart().toolCallId(toolCallId).toolName("get_weather"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("{\""));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("latitude"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("\":"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("48"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("."));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("137"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("154"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta(",\""));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("longitude"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("\":"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("11"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("."));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("576"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("124"));
        streamParts.add(new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("}"));

        // Complete tool input and output
        streamParts.add(
            new StreamToolInputAvailablePart()
                .toolCallId(toolCallId)
                .toolName("get_weather")
                .input("{\"latitude\": 48.137154, \"longitude\": 11.576124}")
        );

        streamParts.add(
            new StreamToolOutputAvailablePart()
                .toolCallId(toolCallId)
                .output(
                    "{\"location\": {\"latitude\": 48.137154, \"longitude\": 11.576124, \"timezone\": \"Europe/Berlin\"}, " +
                    "\"current\": {\"temperature\": 26.8, \"temperature_unit\": \"°C\", \"feels_like\": 25.5, " +
                    "\"humidity\": 35, \"wind_speed\": 9.4, \"wind_direction\": 18, \"pressure\": 1020.4, " +
                    "\"cloud_cover\": 0, \"precipitation\": 0.0, \"weather_code\": 1, \"is_day\": true}, " +
                    "\"daily\": {\"sunrise\": \"2025-06-19T05:13\", \"sunset\": \"2025-06-19T21:16\", " +
                    "\"temperature_max\": 28.0, \"temperature_min\": 16.1}, " +
                    "\"timestamp\": \"2025-06-19T19:30\"}"
                )
        );

        // Final text response after processing tool output
        streamParts.addAll(
            createTextStreamParts(
                "The current weather in Munich is sunny with",
                " a temperature of 26.8°C, feeling like 25.5°C."
            )
        );
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: AI responds with weather tool call and result
        var response = performChatRequest(request);

        // Then: Tool call, result, and text parts are persisted with proper payloads and relationships
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();

        // Verify message has exactly 4 parts (step-start, initial text, tool call+result, final text)
        assertThat(assistantMessage.getParts()).hasSize(4);

        // Verify parts are in correct order
        var parts = assistantMessage.getParts();

        // First part: Step start
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Second part: Initial text
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(1))).isEqualTo(
            "Let me check the current weather for your location in Munich."
        );

        // Third part: Tool call with AI SDK-compatible structure
        var toolCallPart = parts.get(2);
        assertThat(toolCallPart.getType()).isEqualTo(ChatMessagePart.PartType.TOOL);
        assertThat(toolCallPart.getToolName()).isEqualTo("get_weather");
        assertThat(toolCallPart.getToolCallId()).isEqualTo(toolCallId);

        // Verify tool part content matches AI SDK format
        var toolCallContent = toolCallPart.getContent();
        assertThat(toolCallContent.get("toolCallId").asText()).isEqualTo(toolCallId);
        assertThat(toolCallContent.get("type").asText()).isEqualTo("tool-get_weather");
        assertThat(toolCallContent.get("state").asText()).isEqualTo("output-available");
        assertThat(toolCallContent.get("errorText").isNull()).isTrue();

        // Verify tool input is stored
        assertThat(toolCallContent.has("input")).isTrue();
        var toolInput = toolCallContent.get("input");
        assertThat(toolInput.has("latitude")).isTrue();
        assertThat(toolInput.get("latitude").asDouble()).isEqualTo(48.137154);
        assertThat(toolInput.get("longitude").asDouble()).isEqualTo(11.576124);

        // Verify tool output is stored
        assertThat(toolCallContent.has("output")).isTrue();
        var toolOutput = toolCallContent.get("output");

        // Verify specific data points from weather result
        assertThat(toolOutput.get("location").get("timezone").asText()).isEqualTo("Europe/Berlin");
        assertThat(toolOutput.get("current").get("temperature").asDouble()).isEqualTo(26.8);
        assertThat(toolOutput.get("current").get("humidity").asInt()).isEqualTo(35);
        assertThat(toolOutput.get("daily").get("temperature_max").asDouble()).isEqualTo(28.0);

        // Fourth part: Final text response
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(3))).isEqualTo(
            "The current weather in Munich is sunny with a temperature of 26.8°C, feeling like 25.5°C."
        );
    }

    @Test
    @WithMentorUser
    void testSourceCitationPersistence() {
        // Given: A user asking for information with sources
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("According to recent research, "));
        streamParts.add(new StreamSourceUrlPart().url("https://example.com/research").title("Research Paper"));
        streamParts.addAll(createTextStreamParts("the findings show that "));
        streamParts.add(new StreamSourceDocumentPart().sourceId("doc-123").title("Internal Document"));
        streamParts.addAll(createTextStreamParts("we can conclude the following."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: AI responds with text and source citations
        var response = performChatRequest(request);

        // Then: Source URL and source document parts are correctly persisted with their metadata
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        assertThat(assistantMessage.getParts()).hasSize(6); // step-start + 3 text parts + 2 source parts

        var parts = assistantMessage.getParts();

        // Verify part: 0 - step-start
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 1 - text
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(1))).isEqualTo("According to recent research, ");

        // Verify part: 2 - source URL
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_URL);
        assertThat(parts.get(2).getContent().get("url").asText()).isEqualTo("https://example.com/research");
        assertThat(parts.get(2).getContent().get("title").asText()).isEqualTo("Research Paper");

        // Verify part: 3 - text
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(3))).isEqualTo("the findings show that ");

        // Verify part: 4 - source document
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_DOCUMENT);
        assertThat(parts.get(4).getContent().get("sourceId").asText()).isEqualTo("doc-123");
        assertThat(parts.get(4).getContent().get("title").asText()).isEqualTo("Internal Document");

        // Verify part: 5 - text
        assertThat(parts.get(5).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(5))).isEqualTo("we can conclude the following.");
    }

    @Test
    @WithMentorUser
    void testFileAttachmentPersistence() {
        // Given: A chat where the AI generates a file
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String fileUrl = "https://example.com/files/example.txt";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("I've created a file for you:"));
        streamParts.add(new StreamFilePart().url(fileUrl).mediaType("text/plain"));
        streamParts.addAll(createTextStreamParts("Please review the contents."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The file is included in the response stream
        var response = performChatRequest(request);

        // Then: The file part is persisted with correct metadata and retrievable content
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        assertThat(assistantMessage.getParts()).hasSize(4); // step-start + text + file + text

        var parts = assistantMessage.getParts();

        // Verify part: 0 - step-start
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 1 - text
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(1))).isEqualTo("I've created a file for you:");

        // Verify part: 2 - file
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.FILE);
        assertThat(parts.get(2).getContent().get("url").asText()).isEqualTo(fileUrl);
        assertThat(parts.get(2).getContent().get("mediaType").asText()).isEqualTo("text/plain");

        // Verify part: 3 - text
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(3))).isEqualTo("Please review the contents.");
    }

    @Test
    @WithMentorUser
    void testDataPartWithIdReplacementUpdate() {
        // Given: A chat where the AI generates a data part with ID that gets replaced
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String dataId = "chart-123";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Generating chart:"));
        streamParts.add(
            new StreamDataPart().type("data-chart").id(dataId).data("{\"type\": \"bar\", \"values\": [1,2,3]}")
        );
        streamParts.add(
            new StreamDataPart().type("data-chart").id(dataId).data("{\"type\": \"line\", \"values\": [4,5,6]}")
        ); // Replacement
        streamParts.addAll(createTextStreamParts("Chart completed."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: AI generates data part with same ID (replacement pattern)
        var response = performChatRequest(request);

        // Then: The data part is replaced, not duplicated
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        assertThat(assistantMessage.getParts()).hasSize(4); // step-start + text + data (replaced) + text

        var parts = assistantMessage.getParts();

        // Verify part: 0 - step-start
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 1 - text
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(1))).isEqualTo("Generating chart:");

        // Verify part: 2 - data (should be replaced content)
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.DATA);
        assertThat(parts.get(2).getOriginalType()).isEqualTo("data-chart");
        assertThat(parts.get(2).getContent().get("type").asText()).isEqualTo("data-chart");
        assertThat(parts.get(2).getContent().get("id").asText()).isEqualTo(dataId);

        // Check if data field exists and verify it's the replacement data
        assertThat(parts.get(2).getContent().has("data")).isTrue();
        var dataField = parts.get(2).getContent().get("data");

        // Since JSON data is parsed, check the structure
        assertThat(dataField.get("type").asText()).isEqualTo("line");
        assertThat(dataField.get("values").toString()).isEqualTo("[4,5,6]");

        // Verify part: 3 - text
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(3))).isEqualTo("Chart completed.");
    }

    @Test
    @WithMentorUser
    void testMultiStepResponsePersistence() {
        // Given: A complex user query requiring multiple steps
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(
            createReasoningStreamParts("I need to search for ", "information related to the user's query.")
        );
        streamParts.addAll(createTextStreamParts("Let me start by ", "searching for relevant data."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Here are", " the results I found:"));
        streamParts.add(new StreamDataPart().type("data-chart").data("{\"type\": \"bar\", \"values\": [1,2,3]}"));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createReasoningStreamParts("Now I'll analyze the results."));
        streamParts.addAll(createTextStreamParts("Based on the search, ", "here's the conclusion."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: AI responds with multiple step-start/step-finish blocks
        var response = performChatRequest(request);

        // Then: Steps are preserved as distinct groups in the persistence layer
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();

        // Verify we have parts from multiple steps with step-start markers
        assertThat(assistantMessage.getParts().size()).isEqualTo(9);

        var parts = assistantMessage.getParts();

        // Verify part: 0 - step-start (first step)
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 1 - reasoning
        assertThat(parts.get(1).getType()).isEqualTo(ChatMessagePart.PartType.REASONING);
        assertThat(extractTextContent(parts.get(1))).isEqualTo(
            "I need to search for information related to the user's query."
        );

        // Verify part: 2 - text
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(2))).isEqualTo("Let me start by searching for relevant data.");

        // Verify part: 3 - step-start (second step)
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 4 - text
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(4))).isEqualTo("Here are the results I found:");

        // Verify part: 5 - data
        assertThat(parts.get(5).getType()).isEqualTo(ChatMessagePart.PartType.DATA);
        assertThat(parts.get(5).getContent().get("type").asText()).isEqualTo("data-chart");
        // Check that data field is parsed JSON object
        var dataField = parts.get(5).getContent().get("data");
        assertThat(dataField.get("type").asText()).isEqualTo("bar");
        assertThat(dataField.get("values").toString()).isEqualTo("[1,2,3]");

        // Verify part: 6 - step-start (third step)
        assertThat(parts.get(6).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify part: 7 - reasoning
        assertThat(parts.get(7).getType()).isEqualTo(ChatMessagePart.PartType.REASONING);
        assertThat(extractTextContent(parts.get(7))).isEqualTo("Now I'll analyze the results.");

        // Verify part: 8 - text
        assertThat(parts.get(8).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(8))).isEqualTo("Based on the search, here's the conclusion.");
    }

    @Test
    @WithMentorUser
    void testMultipleAssistantMessagePersistence() {
        // Given: A the assistant responds with multiple start messageId parts
        var request = createChatRequest();
        String responseMessageId1 = UUID.randomUUID().toString();
        String responseMessageId2 = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId1));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("This is the ", "first message."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamStartPart().messageId(responseMessageId2));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("This is the ", "second message."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: AI responds with multiple step-start/step-finish blocks
        var response = performChatRequest(request);

        // Then: Steps are preserved as distinct groups in the persistence layer
        StepVerifier.create(response).expectComplete();

        var thread = chatThreadRepository.findById(UUID.fromString(request.id())).orElseThrow();
        var userMessage = chatMessageRepository.findById(UUID.fromString(request.message().getId())).orElseThrow();
        var assistantMessage1 = chatMessageRepository.findById(UUID.fromString(responseMessageId1)).orElseThrow();
        var assistantMessage2 = chatMessageRepository.findById(UUID.fromString(responseMessageId2)).orElseThrow();

        // Verify first assistant message has step-start + text
        assertThat(assistantMessage1.getParts()).hasSize(2);
        assertThat(assistantMessage1.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(assistantMessage1.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(assistantMessage1.getParts().get(1))).isEqualTo("This is the first message.");

        // Verify second assistant message has step-start + text
        assertThat(assistantMessage2.getParts()).hasSize(2);
        assertThat(assistantMessage2.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(assistantMessage2.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(assistantMessage2.getParts().get(1))).isEqualTo("This is the second message.");

        // Verify relationships
        assertThat(assistantMessage1.getThread()).isEqualTo(thread);
        assertThat(assistantMessage2.getThread()).isEqualTo(thread);
        assertThat(assistantMessage1.getParentMessage()).isEqualTo(userMessage);
        assertThat(assistantMessage2.getParentMessage()).isEqualTo(assistantMessage1);
        assertThat(thread.getSelectedLeafMessage()).isEqualTo(assistantMessage2);
    }

    @Test
    @WithMentorUser
    void testVeryLargeUserRequestsAreRejected() {
        // Given: A user request sends an extremely long message (>20000 characters)
        var largeContent = "x".repeat(25000); // 25,000 characters
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var message = new UIMessage();
        message.setId(requestMessageId.toString());
        message.setRole(UIMessage.RoleEnum.USER);
        message.addPartsItem(new UIMessagePartsInner().type("text").text(largeContent));

        var request = new ChatRequestDTO(requestThreadId.toString(), message, null);

        // When: The request is processed by the chat service
        // Then: The request is rejected with an appropriate error message
        webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Verify no messages were persisted
        var threadOptional = chatThreadRepository.findById(requestThreadId);
        assertThat(threadOptional).isEmpty();
    }

    @ParameterizedTest
    @WithMentorUser
    @ValueSource(strings = { "", "   ", "\n\t  " })
    void testHandlingEmptyOrWhitespaceOnlyMessages(String messageContent) {
        // Given: A user sends an empty message or a message with only whitespace
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var emptyMessage = new UIMessage();
        emptyMessage.setId(requestMessageId.toString());
        emptyMessage.setRole(UIMessage.RoleEnum.USER);
        emptyMessage.addPartsItem(new UIMessagePartsInner().type("text").text(messageContent));

        var request = new ChatRequestDTO(requestThreadId.toString(), emptyMessage, null);

        // When: The message is processed
        // Then: The system does not persist the message and returns an appropriate error response
        webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Verify no messages were persisted
        var threadOptional = chatThreadRepository.findById(requestThreadId);
        var messageOptional = chatMessageRepository.findById(requestMessageId);

        assertThat(threadOptional).isEmpty();
        assertThat(messageOptional).isEmpty();
    }

    @Test
    @WithMentorUser
    void testMessageMetadataIsPersisted() {
        // Given: A chat request and a response with message metadata
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String metadataJson =
            "{\"modelName\": \"gpt-4\", \"finishReason\": \"complete\", \"tokens\": {\"prompt\": 45, \"completion\": 78}}";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamMessageMetadataPart().messageMetadata(metadataJson));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Here's your response with metadata."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The message with metadata is processed
        var response = performChatRequest(request);

        // Then: The metadata is correctly persisted along with the message
        StepVerifier.create(response).expectComplete();

        // Retrieve the message from the repository
        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();

        // Check the message has metadata associated with it
        assertThat(assistantMessage.getMetadata()).isNotNull();
        var metadata = assistantMessage.getMetadata();

        // Verify specific metadata fields
        assertThat(metadata.get("modelName").asText()).isEqualTo("gpt-4");
        assertThat(metadata.get("finishReason").asText()).isEqualTo("complete");
        assertThat(metadata.get("tokens").get("prompt").asInt()).isEqualTo(45);
        assertThat(metadata.get("tokens").get("completion").asInt()).isEqualTo(78);

        // Ensure the text response is also properly saved
        var textPart = assistantMessage
            .getParts()
            .stream()
            .filter(part -> part.getType() == ChatMessagePart.PartType.TEXT)
            .findFirst()
            .orElseThrow();
        assertThat(extractTextContent(textPart)).isEqualTo("Here's your response with metadata.");
    }

    @Test
    @WithMentorUser
    void testMultipleStepsWithTextResponsePersistence() {
        // Given: A chat that will have multiple steps (like AI SDK server-side tool roundtrip)
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        // First step with tool call
        streamParts.add(new StreamStepStartPart());
        streamParts.add(
            new StreamToolInputAvailablePart()
                .toolCallId("tool-call-id")
                .toolName("weather")
                .input("{\"city\": \"London\"}")
        );
        streamParts.add(
            new StreamToolOutputAvailablePart().toolCallId("tool-call-id").output("{\"weather\": \"sunny\"}")
        );
        streamParts.add(new StreamStepFinishPart());
        // Second step with text response
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("The weather in London is sunny."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with multiple steps
        var response = performChatRequest(request);

        // Then: Message should have proper structure matching AI SDK expectations
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 4 parts: step-start + tool + step-start + text (per AI SDK structure)
        assertThat(parts).hasSize(4);

        // First step-start
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Tool part with proper AI SDK structure
        var toolPart = parts.get(1);
        assertThat(toolPart.getType()).isEqualTo(ChatMessagePart.PartType.TOOL);
        assertThat(toolPart.getContent().get("toolCallId").asText()).isEqualTo("tool-call-id");
        assertThat(toolPart.getContent().get("type").asText()).isEqualTo("tool-weather");
        assertThat(toolPart.getContent().get("state").asText()).isEqualTo("output-available");
        assertThat(toolPart.getContent().get("input").get("city").asText()).isEqualTo("London");
        assertThat(toolPart.getContent().get("output").get("weather").asText()).isEqualTo("sunny");

        // Second step-start
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Final text response
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(3))).isEqualTo("The weather in London is sunny.");
    }

    @Test
    @WithMentorUser
    void testToolCallErrorHandlingPersistence() {
        // Given: A tool call that will result in an error
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String toolCallId = "error-tool-call";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.add(
            new StreamToolInputAvailablePart()
                .toolCallId(toolCallId)
                .toolName("broken_tool")
                .input("{\"query\": \"test\"}")
        );
        streamParts.add(
            new StreamToolOutputErrorPart().toolCallId(toolCallId).errorText("Tool execution failed: Network timeout")
        );
        streamParts.addAll(
            createTextStreamParts("I apologize, but I encountered an error while trying to fetch that information.")
        );
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with a tool error
        var response = performChatRequest(request);

        // Then: Tool error should be properly persisted with AI SDK structure
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 3 parts: step-start + tool-error + text
        assertThat(parts).hasSize(3);

        // Tool part should have error state
        var toolPart = parts.get(1);
        assertThat(toolPart.getType()).isEqualTo(ChatMessagePart.PartType.TOOL);
        assertThat(toolPart.getContent().get("state").asText()).isEqualTo("output-error");
        assertThat(toolPart.getContent().get("errorText").asText()).isEqualTo("Tool execution failed: Network timeout");
        assertThat(toolPart.getContent().get("output").isNull()).isTrue();
    }

    @Test
    @WithMentorUser
    void testStartAndFinishMetadataMergingPersistence() {
        // Given: A chat with metadata in both start and finish parts
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        // Create metadata objects
        var startMetadata = Map.of("provider", "openai", "model", "gpt-4", "usage", Map.of("prompt_tokens", 10));

        var finishMetadata = Map.of(
            "usage",
            Map.of("prompt_tokens", 10, "completion_tokens", 25, "total_tokens", 35),
            "finish_reason",
            "stop"
        );

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId).messageMetadata(startMetadata));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Here's a response with comprehensive metadata."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart().messageMetadata(finishMetadata));

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with metadata in start and finish
        var response = performChatRequest(request);

        // Then: Metadata should be properly merged per AI SDK expectations
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();

        // Verify merged metadata
        var metadata = assistantMessage.getMetadata();
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("provider").asText()).isEqualTo("openai");
        assertThat(metadata.get("model").asText()).isEqualTo("gpt-4");
        assertThat(metadata.get("finish_reason").asText()).isEqualTo("stop");

        // Usage should be merged with finish taking precedence
        var usage = metadata.get("usage");
        assertThat(usage.get("prompt_tokens").asInt()).isEqualTo(10);
        assertThat(usage.get("completion_tokens").asInt()).isEqualTo(25);
        assertThat(usage.get("total_tokens").asInt()).isEqualTo(35);
    }

    @Test
    @WithMentorUser
    void testFilePartPersistence() {
        // Given: A response that includes file attachments
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Here's the file you requested: "));
        streamParts.add(new StreamFilePart().url("https://example.com/document.pdf").mediaType("application/pdf"));
        streamParts.addAll(createTextStreamParts(" Please review it carefully."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with file parts
        var response = performChatRequest(request);

        // Then: File parts should be properly persisted
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 4 parts: step-start + text + file + text
        assertThat(parts).hasSize(4);

        var filePart = parts.get(2);
        assertThat(filePart.getType()).isEqualTo(ChatMessagePart.PartType.FILE);
        assertThat(filePart.getContent().get("url").asText()).isEqualTo("https://example.com/document.pdf");
        assertThat(filePart.getContent().get("mediaType").asText()).isEqualTo("application/pdf");
    }

    @Test
    @WithMentorUser
    void testDataPartsWithReplacementUpdatesPersistence() {
        // Given: A response with data parts that have ID-based updates (per AI SDK pattern)
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String dataId = "chart-data-1";

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("Creating your chart: "));
        // Initial data part
        streamParts.add(
            new StreamDataPart()
                .id(dataId)
                .type("data-chart")
                .data("{\"type\": \"bar\", \"data\": {\"labels\": [\"A\"], \"values\": [10]}}")
        );
        // Updated data part (should replace the previous one)
        streamParts.add(
            new StreamDataPart()
                .id(dataId)
                .type("data-chart")
                .data("{\"type\": \"bar\", \"data\": {\"labels\": [\"A\", \"B\"], \"values\": [10, 20]}}")
        );
        streamParts.addAll(createTextStreamParts(" Chart completed!"));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with updating data parts
        var response = performChatRequest(request);

        // Then: Only the final state of the data part should be persisted
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 4 parts: step-start + text + data + text (updated data part should replace original)
        assertThat(parts).hasSize(4);

        var dataPart = parts.get(2);
        assertThat(dataPart.getType()).isEqualTo(ChatMessagePart.PartType.DATA);
        assertThat(dataPart.getContent().get("id").asText()).isEqualTo(dataId);
        assertThat(dataPart.getContent().get("type").asText()).isEqualTo("data-chart");

        // Should have the final updated data
        var chartData = dataPart.getContent().get("data");
        assertThat(chartData.get("data").get("labels")).hasSize(2);
        assertThat(chartData.get("data").get("values").get(0).asInt()).isEqualTo(10);
        assertThat(chartData.get("data").get("values").get(1).asInt()).isEqualTo(20);
    }

    @Test
    @WithMentorUser
    void testComplexReasoningWorkflowPersistence() {
        // Given: A complex reasoning workflow (similar to AI SDK reasoning tests)
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        // Reasoning phase
        streamParts.addAll(
            createReasoningStreamParts(
                "Let me think about this problem step by step. ",
                "First, I need to understand what you're asking. ",
                "The question involves multiple components that I should analyze separately. ",
                "After careful consideration, I can provide a comprehensive answer."
            )
        );
        // Response phase with sources
        streamParts.addAll(createTextStreamParts("Based on my analysis, "));
        streamParts.add(
            new StreamSourceUrlPart().url("https://research.example.com/study").title("Research Study on the Topic")
        );
        streamParts.addAll(createTextStreamParts(" and internal documentation "));
        streamParts.add(new StreamSourceDocumentPart().sourceId("internal-doc-456").title("Internal Knowledge Base"));
        streamParts.addAll(createTextStreamParts(", I can conclude that the answer is complex but achievable."));
        streamParts.add(new StreamStepFinishPart());
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI responds with reasoning and sources
        var response = performChatRequest(request);

        // Then: All parts should be properly structured and persisted
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 7 parts: step-start + reasoning + text + source-url + text + source-doc + text
        assertThat(parts).hasSize(7);

        // Verify step-start part
        assertThat(parts.get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);

        // Verify reasoning part (accumulated from multiple chunks)
        var reasoningPart = parts.get(1);
        assertThat(reasoningPart.getType()).isEqualTo(ChatMessagePart.PartType.REASONING);
        String expectedReasoning =
            "Let me think about this problem step by step. " +
            "First, I need to understand what you're asking. " +
            "The question involves multiple components that I should analyze separately. " +
            "After careful consideration, I can provide a comprehensive answer.";
        assertThat(extractTextContent(reasoningPart)).isEqualTo(expectedReasoning);

        // Verify first text part
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(2))).isEqualTo("Based on my analysis, ");

        // Verify source URL part
        var sourceUrlPart = parts.get(3);
        assertThat(sourceUrlPart.getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_URL);
        assertThat(sourceUrlPart.getContent().get("url").asText()).isEqualTo("https://research.example.com/study");
        assertThat(sourceUrlPart.getContent().get("title").asText()).isEqualTo("Research Study on the Topic");

        // Verify second text part
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(4))).isEqualTo(" and internal documentation ");

        // Verify source document part
        var sourceDocPart = parts.get(5);
        assertThat(sourceDocPart.getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_DOCUMENT);
        assertThat(sourceDocPart.getContent().get("sourceId").asText()).isEqualTo("internal-doc-456");
        assertThat(sourceDocPart.getContent().get("title").asText()).isEqualTo("Internal Knowledge Base");

        // Verify final text part
        assertThat(parts.get(6).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(extractTextContent(parts.get(6))).isEqualTo(
            ", I can conclude that the answer is complex but achievable."
        );
    }

    @Test
    @WithMentorUser
    void testStreamErrorRecoveryPersistence() {
        // Given: A stream that encounters an error mid-way
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();

        List<Object> streamParts = new ArrayList<>();
        streamParts.add(new StreamStartPart().messageId(responseMessageId));
        streamParts.add(new StreamStepStartPart());
        streamParts.addAll(createTextStreamParts("I'm processing your request... "));
        streamParts.add(new StreamErrorPart().errorText("Network connection timeout"));
        streamParts.add(new StreamFinishPart());

        mockResponseHolder.setStreamParts(request.message().getId(), streamParts);

        // When: The AI stream encounters an error
        var response = performChatRequest(request);

        // Then: Error should be handled gracefully and persisted
        StepVerifier.create(response).expectComplete();

        var assistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        var parts = assistantMessage.getParts();

        // Should have 3 parts: step-start + text + error-text
        assertThat(parts).hasSize(3);

        // Verify error is captured as text part
        var errorPart = parts.get(2);
        assertThat(errorPart.getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(errorPart.getContent().asText()).contains("Network connection timeout");
    }

    // Helper methods
    private User createTestUser() {
        // Fetch the "mentor" user that was seeded by TestUserConfig
        return userRepository
            .findByLogin("mentor")
            .orElseThrow(() -> new IllegalStateException("Test mentor user not found in database"));
    }

    private ChatMessage createMessageInThread(
        ChatThread thread,
        ChatMessage.Role role,
        String content,
        ChatMessage parent
    ) {
        var message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setThread(thread);
        message.setRole(role);
        if (parent != null) {
            message.setParentMessage(parent);
        }

        var part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(message.getId(), 0));
        part.setMessage(message);
        part.setType(ChatMessagePart.PartType.TEXT);
        part.setContent(objectMapper.createObjectNode().put("text", content));

        message.setParts(List.of(part));
        return chatMessageRepository.save(message);
    }

    private ChatRequestDTO createChatRequest() {
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var message = new UIMessage();
        message.setId(requestMessageId.toString());
        message.setRole(UIMessage.RoleEnum.USER);
        message.addPartsItem(new UIMessagePartsInner().type("text").text("Hello, World!"));

        return new ChatRequestDTO(requestThreadId.toString(), message, null);
    }

    private Flux<String> performChatRequest(ChatRequestDTO request) {
        // This will return the response Flux for assertions
        return webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(String.class)
            .getResponseBody();
    }
}

package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
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
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Hello,"),
                new StreamTextPart().text(" this"),
                new StreamTextPart().text(" is"),
                new StreamTextPart().text(" a"),
                new StreamTextPart().text(" test!"),
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
        assertThat(thread.getTitle()).isEqualTo("New chat");

        // User message should have been created with the request ID
        ChatMessage userMessage = chatMessageRepository
            .findById(UUID.fromString(request.messages().getLast().getId()))
            .orElseThrow(() -> new AssertionError("No user message from request found"));
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts()).hasSize(1); // Only one part for the user message

        ChatMessagePart userPart = userMessage.getParts().get(0);
        assertThat(userPart.getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(userPart.getContent().asText()).isEqualTo("Hello, World!");

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
        assertThat(parts.get(1).getContent().asText()).isEqualTo("Hello, this is a test!");

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
        var existingAssistantMessage = createMessageInThread(existingThread, ChatMessage.Role.ASSISTANT, "1+1 is 2", existingUserMessage);

        existingThread.setSelectedLeafMessage(existingAssistantMessage);
        chatThreadRepository.save(existingThread);

        String responseMessageId = UUID.randomUUID().toString();
        
        var followUpMessage = new UIMessage();
        followUpMessage.setId(UUID.randomUUID().toString());
        followUpMessage.setRole(UIMessage.RoleEnum.USER);
        followUpMessage.addPartsItem(new UIMessagePartsInner().type("text").text("Follow-up question, what is 1+2?"));
        
        var request = new ChatRequestDTO(existingThread.getId().toString(), 
            List.of(
                existingUserMessage.toUIMessage(),
                existingAssistantMessage.toUIMessage(),
                followUpMessage
            )
        );

        mockResponseHolder.setStreamParts(
            followUpMessage.getId().toString(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Thank you for the follow-up. "),
                new StreamTextPart().text("This builds on our previous conversation. "),
                new StreamTextPart().text("Now, 1+2 is 3."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(newUserMessage.getParts().get(0).getContent().asText())
            .isEqualTo("Follow-up question, what is 1+2?");
        
        var newAssistantMessage = chatMessageRepository.findById(UUID.fromString(responseMessageId)).orElseThrow();
        assertThat(newAssistantMessage.getThread()).isEqualTo(refreshedThread);
        assertThat(newAssistantMessage.getParentMessage().getId()).isEqualTo(newUserMessage.getId());
        assertThat(newAssistantMessage.getParts()).hasSize(2); // step-start + text
        assertThat(newAssistantMessage.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(newAssistantMessage.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(newAssistantMessage.getParts().get(1).getContent().asText())
            .isEqualTo("Thank you for the follow-up. This builds on our previous conversation. Now, 1+2 is 3.");

        assertThat(refreshedThread.getSelectedLeafMessage()).isEqualTo(newAssistantMessage);
    }

    @Test
    @WithMentorUser
    void testReasoningPartPersistence() {
        // Given: A chat with a user question requiring explanation
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamReasoningPart().text("Let me think about this step by step. "),
                new StreamReasoningPart().text("First, I need to analyze the problem. "),
                new StreamReasoningPart().text("Then I'll provide a solution."),
                new StreamReasoningFinishPart(),
                new StreamTextPart().text("Based on my analysis, "),
                new StreamTextPart().text("here's the answer."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText())
            .isEqualTo("Let me think about this step by step. First, I need to analyze the problem. Then I'll provide a solution.");
        
        // Verify text part
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(2).getContent().asText()).isEqualTo("Based on my analysis, here's the answer.");
    }

    @Test
    @WithMentorUser
    void testToolCallAndToolResultPersistence() {
        // Given: A user message requesting weather information
        var request = createChatRequest();
        String responseMessageId = "0ea53ae4-a149-4428-8d56-b76a872b4678";
        String toolCallId = "call_dGexcNCb8AVioJpEN7U8L8KE";
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                // Initial message and first text part
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Let me check the current weather "),
                new StreamTextPart().text("for your location in Munich."),

                // Tool call - Weather API - Input
                new StreamToolInputStartPart().toolCallId(toolCallId).toolName("get_weather"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("{\""),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("latitude"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("\":"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("48"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("."),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("137"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("154"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta(",\""),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("longitude"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("\":"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("11"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("."),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("576"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("124"),
                new StreamToolInputDeltaPart().toolCallId(toolCallId).inputTextDelta("}"),
                
                // Complete tool input and output
                new StreamToolInputAvailablePart()
                    .toolCallId(toolCallId)
                    .toolName("get_weather")
                    .input("{\"latitude\": 48.137154, \"longitude\": 11.576124}"),
                    
                new StreamToolOutputAvailablePart()
                    .toolCallId(toolCallId)
                    .output("{\"location\": {\"latitude\": 48.137154, \"longitude\": 11.576124, \"timezone\": \"Europe/Berlin\"}, " +
                           "\"current\": {\"temperature\": 26.8, \"temperature_unit\": \"°C\", \"feels_like\": 25.5, " +
                           "\"humidity\": 35, \"wind_speed\": 9.4, \"wind_direction\": 18, \"pressure\": 1020.4, " +
                           "\"cloud_cover\": 0, \"precipitation\": 0.0, \"weather_code\": 1, \"is_day\": true}, " +
                           "\"daily\": {\"sunrise\": \"2025-06-19T05:13\", \"sunset\": \"2025-06-19T21:16\", " +
                           "\"temperature_max\": 28.0, \"temperature_min\": 16.1}, " +
                           "\"timestamp\": \"2025-06-19T19:30\"}"),
                
                // Final text response after processing tool output
                new StreamTextPart().text("The current weather in Munich is sunny with"),
                new StreamTextPart().text(" a temperature of 26.8°C, feeling like 25.5°C."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText())
            .isEqualTo("Let me check the current weather for your location in Munich.");
        
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
        assertThat(parts.get(3).getContent().asText())
            .isEqualTo("The current weather in Munich is sunny with a temperature of 26.8°C, feeling like 25.5°C.");
    }

    @Test
    @WithMentorUser
    void testSourceCitationPersistence() {
        // Given: A user asking for information with sources
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("According to recent research, "),
                new StreamSourceUrlPart().url("https://example.com/research").title("Research Paper"),
                new StreamTextPart().text("the findings show that "),
                new StreamSourceDocumentPart().sourceId("doc-123").title("Internal Document"),
                new StreamTextPart().text("we can conclude the following."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText()).isEqualTo("According to recent research, ");

        // Verify part: 2 - source URL
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_URL);
        assertThat(parts.get(2).getContent().get("url").asText()).isEqualTo("https://example.com/research");
        assertThat(parts.get(2).getContent().get("title").asText()).isEqualTo("Research Paper");

        // Verify part: 3 - text
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(3).getContent().asText()).isEqualTo("the findings show that ");

        // Verify part: 4 - source document
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_DOCUMENT);
        assertThat(parts.get(4).getContent().get("sourceId").asText()).isEqualTo("doc-123");
        assertThat(parts.get(4).getContent().get("title").asText()).isEqualTo("Internal Document");

        // Verify part: 5 - text
        assertThat(parts.get(5).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(5).getContent().asText()).isEqualTo("we can conclude the following.");
    }

    @Test
    @WithMentorUser
    void testFileAttachmentPersistence() {
        // Given: A chat where the AI generates a file
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String fileUrl = "https://example.com/files/example.txt";
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("I've created a file for you:"),
                new StreamFilePart().url(fileUrl).mediaType("text/plain"),
                new StreamTextPart().text("Please review the contents."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText())
            .isEqualTo("I've created a file for you:");

        // Verify part: 2 - file 
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.FILE);
        assertThat(parts.get(2).getContent().get("url").asText()).isEqualTo(fileUrl);
        assertThat(parts.get(2).getContent().get("mediaType").asText()).isEqualTo("text/plain");
    
        // Verify part: 3 - text
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(3).getContent().asText())
            .isEqualTo("Please review the contents.");
    }

    @Test
    @WithMentorUser
    void testDataPartWithIdReplacementUpdate() {
        // Given: A chat where the AI generates a data part with ID that gets replaced
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String dataId = "chart-123";
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Generating chart:"),
                new StreamDataPart().type("data-chart").id(dataId).data("{\"type\": \"bar\", \"values\": [1,2,3]}"),
                new StreamDataPart().type("data-chart").id(dataId).data("{\"type\": \"line\", \"values\": [4,5,6]}"), // Replacement
                new StreamTextPart().text("Chart completed."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText()).isEqualTo("Generating chart:");

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
        assertThat(parts.get(3).getContent().asText()).isEqualTo("Chart completed.");
    }

    @Test
    @WithMentorUser
    void testMultiStepResponsePersistence() {
        // Given: A complex user query requiring multiple steps
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamReasoningPart().text("I need to search for "),
                new StreamReasoningPart().text("information related to the user's query."),
                new StreamReasoningFinishPart(),
                new StreamTextPart().text("Let me start by "),
                new StreamTextPart().text("searching for relevant data."),
                new StreamStepFinishPart(),
                new StreamStepStartPart(),
                new StreamTextPart().text("Here are"),
                new StreamTextPart().text(" the results I found:"),
                new StreamDataPart().type("data-chart").data("{\"type\": \"bar\", \"values\": [1,2,3]}"),
                new StreamStepFinishPart(),
                new StreamStepStartPart(),
                new StreamReasoningPart().text("Now I'll analyze the results."),
                new StreamReasoningFinishPart(),
                new StreamTextPart().text("Based on the search, "),
                new StreamTextPart().text("here's the conclusion."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(1).getContent().asText())
            .isEqualTo("I need to search for information related to the user's query.");
            
        // Verify part: 2 - text
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(2).getContent().asText())
            .isEqualTo("Let me start by searching for relevant data.");
        
        // Verify part: 3 - step-start (second step)
        assertThat(parts.get(3).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        
        // Verify part: 4 - text
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(4).getContent().asText())
            .isEqualTo("Here are the results I found:");
            
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
        assertThat(parts.get(7).getContent().asText())
            .isEqualTo("Now I'll analyze the results.");
            
        // Verify part: 8 - text
        assertThat(parts.get(8).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(8).getContent().asText())
            .isEqualTo("Based on the search, here's the conclusion.");
    }

    @Test
    @WithMentorUser
    void testMultipleAssistantMessagePersistence() {
        // Given: A the assistant responds with multiple start messageId parts
        var request = createChatRequest();
        String responseMessageId1 = UUID.randomUUID().toString();
        String responseMessageId2 = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId1),
                new StreamStepStartPart(),
                new StreamTextPart().text("This is the "),
                new StreamTextPart().text("first message."),
                new StreamStepFinishPart(),
                new StreamStartPart().messageId(responseMessageId2),
                new StreamStepStartPart(),
                new StreamTextPart().text("This is the "),
                new StreamTextPart().text("second message."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

        // When: AI responds with multiple step-start/step-finish blocks
        var response = performChatRequest(request);
        
        // Then: Steps are preserved as distinct groups in the persistence layer
        StepVerifier.create(response).expectComplete();
        
        var thread = chatThreadRepository.findById(UUID.fromString(request.id())).orElseThrow();
        var userMessage = chatMessageRepository.findById(UUID.fromString(request.messages().getLast().getId())).orElseThrow();
        var assistantMessage1 = chatMessageRepository.findById(UUID.fromString(responseMessageId1)).orElseThrow();
        var assistantMessage2 = chatMessageRepository.findById(UUID.fromString(responseMessageId2)).orElseThrow();

        // Verify first assistant message has step-start + text
        assertThat(assistantMessage1.getParts()).hasSize(2);
        assertThat(assistantMessage1.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(assistantMessage1.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(assistantMessage1.getParts().get(1).getContent().asText())
            .isEqualTo("This is the first message.");

        // Verify second assistant message has step-start + text
        assertThat(assistantMessage2.getParts()).hasSize(2);
        assertThat(assistantMessage2.getParts().get(0).getType()).isEqualTo(ChatMessagePart.PartType.STEP_START);
        assertThat(assistantMessage2.getParts().get(1).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(assistantMessage2.getParts().get(1).getContent().asText())
            .isEqualTo("This is the second message.");

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

        var request = new ChatRequestDTO(requestThreadId.toString(), List.of(message));

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
    @ValueSource(strings = {"", "   ", "\n\t  "})
    void testHandlingEmptyOrWhitespaceOnlyMessages(String messageContent) {
        // Given: A user sends an empty message or a message with only whitespace
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var emptyMessage = new UIMessage();
        emptyMessage.setId(requestMessageId.toString());
        emptyMessage.setRole(UIMessage.RoleEnum.USER);
        emptyMessage.addPartsItem(new UIMessagePartsInner().type("text").text(messageContent));

        var request = new ChatRequestDTO(requestThreadId.toString(), List.of(emptyMessage));

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
        String metadataJson = "{\"modelName\": \"gpt-4\", \"finishReason\": \"complete\", \"tokens\": {\"prompt\": 45, \"completion\": 78}}";

        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamMessageMetadataPart().messageMetadata(metadataJson),
                new StreamStepStartPart(),
                new StreamTextPart().text("Here's your response with metadata."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        var textPart = assistantMessage.getParts().stream()
            .filter(part -> part.getType() == ChatMessagePart.PartType.TEXT)
            .findFirst().orElseThrow();
        assertThat(textPart.getContent().asText()).isEqualTo("Here's your response with metadata.");
    }

    @Test
    @WithMentorUser
    void testMultipleStepsWithTextResponsePersistence() {
        // Given: A chat that will have multiple steps (like AI SDK server-side tool roundtrip)
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                // First step with tool call
                new StreamStepStartPart(),
                new StreamToolInputAvailablePart()
                    .toolCallId("tool-call-id")
                    .toolName("weather")
                    .input("{\"city\": \"London\"}"),
                new StreamToolOutputAvailablePart()
                    .toolCallId("tool-call-id")
                    .output("{\"weather\": \"sunny\"}"),
                new StreamStepFinishPart(),
                // Second step with text response  
                new StreamStepStartPart(),
                new StreamTextPart().text("The weather in London is sunny."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        assertThat(parts.get(3).getContent().asText()).isEqualTo("The weather in London is sunny.");
    }

    @Test
    @WithMentorUser  
    void testToolCallErrorHandlingPersistence() {
        // Given: A tool call that will result in an error
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        String toolCallId = "error-tool-call";
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamToolInputAvailablePart()
                    .toolCallId(toolCallId)
                    .toolName("broken_tool")
                    .input("{\"query\": \"test\"}"),
                new StreamToolOutputErrorPart()
                    .toolCallId(toolCallId)
                    .errorText("Tool execution failed: Network timeout"),
                new StreamTextPart().text("I apologize, but I encountered an error while trying to fetch that information."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        var startMetadata = Map.of(
            "provider", "openai",
            "model", "gpt-4",
            "usage", Map.of("prompt_tokens", 10)
        );
        
        var finishMetadata = Map.of(
            "usage", Map.of(
                "prompt_tokens", 10,
                "completion_tokens", 25,
                "total_tokens", 35
            ),
            "finish_reason", "stop"
        );
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId).messageMetadata(startMetadata),
                new StreamStepStartPart(),
                new StreamTextPart().text("Here's a response with comprehensive metadata."),
                new StreamStepFinishPart(),
                new StreamFinishPart().messageMetadata(finishMetadata)
            )
        );

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
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Here's the file you requested: "),
                new StreamFilePart()
                    .url("https://example.com/document.pdf")
                    .mediaType("application/pdf"),
                new StreamTextPart().text(" Please review it carefully."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Creating your chart: "),
                // Initial data part
                new StreamDataPart()
                    .id(dataId)
                    .type("data-chart")
                    .data("{\"type\": \"bar\", \"data\": {\"labels\": [\"A\"], \"values\": [10]}}"),
                // Updated data part (should replace the previous one)  
                new StreamDataPart()
                    .id(dataId)
                    .type("data-chart")
                    .data("{\"type\": \"bar\", \"data\": {\"labels\": [\"A\", \"B\"], \"values\": [10, 20]}}"),
                new StreamTextPart().text(" Chart completed!"),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                // Reasoning phase
                new StreamReasoningPart().text("Let me think about this problem step by step. "),
                new StreamReasoningPart().text("First, I need to understand what you're asking. "),
                new StreamReasoningPart().text("The question involves multiple components that I should analyze separately. "),
                new StreamReasoningPart().text("After careful consideration, I can provide a comprehensive answer."),
                new StreamReasoningFinishPart(),
                // Response phase with sources
                new StreamTextPart().text("Based on my analysis, "),
                new StreamSourceUrlPart()
                    .url("https://research.example.com/study")
                    .title("Research Study on the Topic"),
                new StreamTextPart().text(" and internal documentation "),
                new StreamSourceDocumentPart()
                    .sourceId("internal-doc-456")
                    .title("Internal Knowledge Base"),
                new StreamTextPart().text(", I can conclude that the answer is complex but achievable."),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

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
        String expectedReasoning = "Let me think about this problem step by step. " +
                                 "First, I need to understand what you're asking. " +
                                 "The question involves multiple components that I should analyze separately. " +
                                 "After careful consideration, I can provide a comprehensive answer.";
        assertThat(reasoningPart.getContent().asText()).isEqualTo(expectedReasoning);
        
        // Verify first text part
        assertThat(parts.get(2).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(2).getContent().asText()).isEqualTo("Based on my analysis, ");
        
        // Verify source URL part
        var sourceUrlPart = parts.get(3);
        assertThat(sourceUrlPart.getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_URL);
        assertThat(sourceUrlPart.getContent().get("url").asText()).isEqualTo("https://research.example.com/study");
        assertThat(sourceUrlPart.getContent().get("title").asText()).isEqualTo("Research Study on the Topic");
        
        // Verify second text part  
        assertThat(parts.get(4).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(4).getContent().asText()).isEqualTo(" and internal documentation ");
        
        // Verify source document part
        var sourceDocPart = parts.get(5);
        assertThat(sourceDocPart.getType()).isEqualTo(ChatMessagePart.PartType.SOURCE_DOCUMENT);
        assertThat(sourceDocPart.getContent().get("sourceId").asText()).isEqualTo("internal-doc-456");
        assertThat(sourceDocPart.getContent().get("title").asText()).isEqualTo("Internal Knowledge Base");
        
        // Verify final text part
        assertThat(parts.get(6).getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(parts.get(6).getContent().asText()).isEqualTo(", I can conclude that the answer is complex but achievable.");
    }

    @Test
    @WithMentorUser
    void testStreamErrorRecoveryPersistence() {
        // Given: A stream that encounters an error mid-way
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("I'm processing your request... "),
                new StreamErrorPart().errorText("Network connection timeout"),
                new StreamFinishPart()
            )
        );

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
        return userRepository.findByLogin("mentor")
            .orElseThrow(() -> new IllegalStateException("Test mentor user not found in database"));
    }
    
    private ChatMessage createMessageInThread(ChatThread thread, ChatMessage.Role role, String content, ChatMessage parent) {
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
        
        return new ChatRequestDTO(requestThreadId.toString(), List.of(message));
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
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();
    }
}

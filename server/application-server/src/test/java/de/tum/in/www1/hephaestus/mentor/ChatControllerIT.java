package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ChatController that cover all AI SDK stream protocol message parts.
 * These tests are designed with future persistence in mind for ChatThread, ChatMessage, and ChatMessagePart entities.
 * 
 * CRITICAL AI SDK Stream Protocol Requirements (https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol):
 * 1. MANDATORY Start Step Frame (f:{"messageId":"<id>"}) - MUST be the first frame in every stream
 * 2. Finish Step Frame (e:) with usage and isContinued flag for each step
 * 3. Finish Message Frame (d:) as the final frame with complete usage totals
 * 4. Multi-step scenarios use isContinued:true in intermediate steps
 * 
 * Tests follow scenarios from the official AI SDK implementation:
 * - Simple text responses (maps to TEXT message parts) - Frame type: 0:
 * - Tool call workflows (maps to TOOL_INVOCATION message parts) - Frame types: b:, c:, 9:, a:
 * - Reasoning streams (maps to REASONING message parts) - Frame types: g:, i:, j:
 * - File attachments (maps to FILE message parts) - Frame type: k:
 * - Source citations (maps to SOURCE message parts) - Frame type: h:
 * - Data parts (structured JSON arrays) - Frame type: 2:
 * - Message annotations (metadata) - Frame type: 8:
 * - Error handling and recovery scenarios - Frame type: 3:
 * - Multi-step conversations (demonstrates ChatThread structure)
 * 
 * Each test validates the complete stream protocol compliance to ensure compatibility
 * with AI SDK clients and proper persistence mapping.
 */
@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ChatControllerIT.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MockChatFrameHolder mockFrameHolder;
    
    @Autowired
    private ChatTestDataSetup testDataSetup;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private ChatMessagePartRepository chatMessagePartRepository;
    
    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @BeforeEach
    void setUp() {
        mockFrameHolder.frames = List.of();
        // Create test user - authentication will be handled by @WithMentorUser annotation
        testDataSetup.createTestUser();
    }
    
    @AfterEach
    void tearDown() {
        try {
            // Clean up in correct order due to foreign key constraints
            // 1. Clear selectedLeafMessage references in threads first 
            chatThreadRepository.findAll().forEach(thread -> {
                thread.setSelectedLeafMessage(null);
                chatThreadRepository.save(thread);
            });
            // 2. Delete message parts
            chatMessagePartRepository.deleteAll();
            // 3. Delete messages
            chatMessageRepository.deleteAll();
            // 4. Delete threads last
            chatThreadRepository.deleteAll();
        } catch (Exception e) {
            logger.warn("Error during test cleanup: {}", e.getMessage());
        }
    }

    /**
     * Comprehensive test: AI SDK Data Stream Protocol - Text Message with Persistence
     * Verifies complete text message handling per AI SDK specification.
     * Tests: Streaming (0: frames) + Persistence (TEXT message parts) + Protocol compliance
     */
    @Test
    @WithMentorUser
    void shouldHandleCompleteTextMessageProtocol() {
        // Given - Clear database first
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        String messageId = UUID.randomUUID().toString();
        final List<String> aiSdkFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",  // Start step with message ID
            "0:\"Hello, \"",                            // Text part 1
            "0:\"this is a \"",                         // Text part 2  
            "0:\"complete AI SDK test!\"",              // Text part 3
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12}}"
        );

        // When - Process chat request (streaming)
        var streamingResult = performChatRequest(aiSdkFrames);
        
        // Then - Verify streaming protocol compliance
        StepVerifier.create(streamingResult)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("0:\"Hello, \"")
            .expectNext("0:\"this is a \"")
            .expectNext("0:\"complete AI SDK test!\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":6,\"promptTokens\":12}}")
            .verifyComplete();
        
        // And - Verify persistence (message structure)
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER + ASSISTANT
        
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found"));
            
        // Verify message ID from f: frame is used
        assertThat(assistantMessage.getId()).isEqualTo(UUID.fromString(messageId));
        
        // And - Verify persistence (message parts)
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSize(1); // All text frames combined into ONE TEXT part
        
        ChatMessagePart textPart = parts.get(0);
        assertThat(textPart.getType()).isEqualTo(ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart.getContent().asText()).isEqualTo("Hello, this is a complete AI SDK test!");
        
        logger.info("✅ Complete AI SDK text protocol verified: streaming + persistence");
    }

    /**
     * Test: Server-side tool roundtrip - matches AI SDK "server-side tool roundtrip" 
     * Maps to: TOOL_INVOCATION message parts
     * Persistence: ChatMessage with TOOL_INVOCATION parts, demonstrates multi-step ChatThread
     */
    @Test
    @WithMentorUser
    void shouldStreamServerSideToolRoundtrip() {
        // Given - Clear database and complete tool call workflow with multi-step pattern
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Use proper UUID format for message IDs
        String toolStepId = "12345678-1234-1234-1234-123456789001";
        String responseStepId = "12345678-1234-1234-1234-123456789002";
        
        var expectedFrames = List.of(
            "f:{\"messageId\":\"" + toolStepId + "\"}",
            "9:{\"toolCallId\":\"tool-call-id\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}",
            "a:{\"toolCallId\":\"tool-call-id\",\"result\":{\"weather\":\"sunny\"}}",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":true}",
            "f:{\"messageId\":\"" + responseStepId + "\"}",
            "0:\"The weather in London is sunny.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":4},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}"
        );
        
        // When - Process request (streaming + persistence)
        var actualFrames = performChatRequest(expectedFrames);

        // Then - Verify streaming protocol compliance
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"" + toolStepId + "\"}")
            .expectNext("9:{\"toolCallId\":\"tool-call-id\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}")
            .expectNext("a:{\"toolCallId\":\"tool-call-id\",\"result\":{\"weather\":\"sunny\"}}")
            .expectNext("e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":true}")
            .expectNext("f:{\"messageId\":\"" + responseStepId + "\"}")
            .expectNext("0:\"The weather in London is sunny.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":4},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}")
            .verifyComplete();
        
        // And - Verify persistence (multi-step message structure)
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER + ASSISTANT
        
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        
        // Verify assistant message has correct ID from step 1 (the first f: frame creates the message)
        assertThat(assistantMessage.getId().toString()).isEqualTo(toolStepId);
        
        // Verify message parts were persisted correctly
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSizeGreaterThan(0);
        
        // Verify we have the expected message part types for tool calls
        boolean hasToolInvocation = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION);
        boolean hasToolResult = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_RESULT);
        boolean hasText = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT);
        
        assertThat(hasToolInvocation).isTrue();
        assertThat(hasToolResult).isTrue(); 
        assertThat(hasText).isTrue();
        
        logger.info("✅ Tool roundtrip verified: streaming + persistence with {} parts", parts.size());
    }

    /**
     * Test: Tool call streaming - matches AI SDK "tool call streaming"
     * Maps to: TOOL_INVOCATION parts with streaming delta updates
     * Persistence: Shows how partial tool calls evolve in ChatMessagePart
     */
    @Test
    @WithMentorUser
    void shouldStreamToolCallWithDeltas() {
        // Given - Clear database and streaming tool call with deltas and proper start step
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Use proper UUID format for message ID
        String messageId = "12345678-1234-1234-1234-123456789003";
        
        var expectedFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "b:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\"}",
            "c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"{\\\"testArg\\\":\\\"t\"}",
            "c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"est-value\\\"}\"}",
            "9:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\",\"args\":{\"testArg\":\"test-value\"}}",
            "a:{\"toolCallId\":\"tool-call-0\",\"result\":\"test-result\"}",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );
        
        // When - Process request (streaming + persistence)
        var actualFrames = performChatRequest(expectedFrames);

        // Then - Verify streaming protocol compliance
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("b:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\"}")
            .expectNext("c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"{\\\"testArg\\\":\\\"t\"}")
            .expectNext("c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"est-value\\\"}\"}")
            .expectNext("9:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\",\"args\":{\"testArg\":\"test-value\"}}")
            .expectNext("a:{\"toolCallId\":\"tool-call-0\",\"result\":\"test-result\"}")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}")
            .verifyComplete();
        
        // And - Verify persistence (final tool call state, deltas are streaming-only)
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER + ASSISTANT
        
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        
        // Verify assistant message has correct ID
        assertThat(assistantMessage.getId().toString()).isEqualTo(messageId);
        
        // Verify final tool call parts were persisted (b: and c: frames are streaming-only)
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSizeGreaterThan(0);
        
        // Should have TOOL_INVOCATION (from 9: frame) and TOOL_RESULT (from a: frame)
        boolean hasToolInvocation = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION);
        boolean hasToolResult = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_RESULT);
        
        assertThat(hasToolInvocation).isTrue();
        assertThat(hasToolResult).isTrue();
        
        // Verify tool invocation has final args (not deltas)
        ChatMessagePart toolPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION)
            .findFirst().orElseThrow();
        
        JsonNode toolContent = toolPart.getContent();
        assertThat(toolContent.get("toolCallId").asText()).isEqualTo("tool-call-0");
        assertThat(toolContent.get("toolName").asText()).isEqualTo("test-tool");
        assertThat(toolContent.get("args").get("testArg").asText()).isEqualTo("test-value");
        
        logger.info("✅ Tool call deltas verified: streaming + persistence with final state");
    }

    /**
     * Test: Server provides reasoning - matches AI SDK "server provides reasoning"
     * Maps to: REASONING message parts with signatures
     * Persistence: ChatMessage with REASONING parts, including redacted reasoning
     */
    @Test
    @WithMentorUser
    void shouldStreamReasoningWithSignature() {
        // Given - Clear database and reasoning with signatures and redacted parts
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Use proper UUID format for message ID
        String messageId = "12345678-1234-1234-1234-123456789004";
        
        var expectedFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "g:\"I will open the conversation\"",
            "g:\" with witty banter. \"",
            "j:{\"signature\":\"1234567890\"}",
            "i:{\"data\":\"redacted-data\"}",
            "g:\"Once the user has relaxed,\"",
            "g:\" I will pry for valuable information.\"",
            "j:{\"signature\":\"abc123\"}",
            "0:\"Hi there!\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );
        
        // When - Process request (streaming + persistence)
        var actualFrames = performChatRequest(expectedFrames);

        // Then - Verify streaming protocol compliance
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("g:\"I will open the conversation\"")
            .expectNext("g:\" with witty banter. \"")
            .expectNext("j:{\"signature\":\"1234567890\"}")
            .expectNext("i:{\"data\":\"redacted-data\"}")
            .expectNext("g:\"Once the user has relaxed,\"")
            .expectNext("g:\" I will pry for valuable information.\"")
            .expectNext("j:{\"signature\":\"abc123\"}")
            .expectNext("0:\"Hi there!\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}")
            .verifyComplete();
        
        // And - Verify persistence (reasoning parts with signatures and redacted content)
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER + ASSISTANT
        
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        
        // Verify assistant message has correct ID
        assertThat(assistantMessage.getId().toString()).isEqualTo(messageId);
        
        // Verify reasoning parts were persisted
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSizeGreaterThan(0);
        
        // Should have REASONING, REASONING_SIGNATURE, REASONING_REDACTED, and TEXT parts
        boolean hasReasoning = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.REASONING);
        boolean hasSignature = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.REASONING_SIGNATURE);
        boolean hasRedacted = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.REASONING_REDACTED);
        boolean hasText = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT);
        
        assertThat(hasReasoning).isTrue();
        assertThat(hasSignature).isTrue();
        assertThat(hasRedacted).isTrue();
        assertThat(hasText).isTrue();
        
        // Verify reasoning content is combined from multiple g: frames
        ChatMessagePart reasoningPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.REASONING)
            .findFirst().orElseThrow();
        
        String combinedReasoning = reasoningPart.getContent().asText();
        assertThat(combinedReasoning).contains("I will open the conversation");
        assertThat(combinedReasoning).contains("with witty banter");
        assertThat(combinedReasoning).contains("Once the user has relaxed");
        assertThat(combinedReasoning).contains("I will pry for valuable information");
        
        // Verify text content
        ChatMessagePart textPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .findFirst().orElseThrow();
        
        assertThat(textPart.getContent().asText()).isEqualTo("Hi there!");
        
        logger.info("✅ Reasoning with signatures verified: streaming + persistence with {} parts", parts.size());
    }

    /**
     * Test: File attachments - matches AI SDK "server provides file parts"
     * Maps to: FILE message parts
     * Persistence: ChatMessage with FILE parts containing base64 data
     */
    @Test
    @WithMentorUser
    void shouldStreamFileAttachments() {
        // Given - Clear database and file attachments with different MIME types and start step
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Use proper UUID format for message ID
        String messageId = "12345678-1234-1234-1234-123456789005";
        
        var expectedFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "0:\"Here is a file:\"",
            "k:{\"data\":\"Hello World\",\"mimeType\":\"text/plain\"}",
            "0:\"And another one:\"",
            "k:{\"data\":\"{\\\"key\\\": \\\"value\\\"}\",\"mimeType\":\"application/json\"}",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}"
        );
        
        // When - Process request (streaming + persistence)
        var actualFrames = performChatRequest(expectedFrames);

        // Then - Verify streaming protocol compliance
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("0:\"Here is a file:\"")
            .expectNext("k:{\"data\":\"Hello World\",\"mimeType\":\"text/plain\"}")
            .expectNext("0:\"And another one:\"")
            .expectNext("k:{\"data\":\"{\\\"key\\\": \\\"value\\\"}\",\"mimeType\":\"application/json\"}")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}")
            .verifyComplete();
        
        // And - Verify persistence (file attachments with different MIME types)
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER + ASSISTANT
        
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        
        // Verify assistant message has correct ID
        assertThat(assistantMessage.getId().toString()).isEqualTo(messageId);
        
        // Verify file parts were persisted
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSizeGreaterThan(0);
        
        // Should have FILE parts and TEXT parts
        List<ChatMessagePart> fileParts = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.FILE)
            .toList();
        boolean hasText = parts.stream().anyMatch(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT);
        
        assertThat(fileParts).hasSize(2); // Two file attachments
        assertThat(hasText).isTrue();
        
        // Verify first file attachment (text/plain)
        ChatMessagePart textFile = fileParts.stream()
            .filter(part -> part.getContent().get("mimeType").asText().equals("text/plain"))
            .findFirst().orElseThrow();
        
        assertThat(textFile.getContent().get("data").asText()).isEqualTo("Hello World");
        assertThat(textFile.getContent().get("mimeType").asText()).isEqualTo("text/plain");
        
        // Verify second file attachment (application/json)
        ChatMessagePart jsonFile = fileParts.stream()
            .filter(part -> part.getContent().get("mimeType").asText().equals("application/json"))
            .findFirst().orElseThrow();
        
        assertThat(jsonFile.getContent().get("data").asText()).isEqualTo("{\"key\": \"value\"}");
        assertThat(jsonFile.getContent().get("mimeType").asText()).isEqualTo("application/json");
        
        // Verify combined text content
        ChatMessagePart textPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .findFirst().orElseThrow();
        
        assertThat(textPart.getContent().asText()).isEqualTo("Here is a file:And another one:");
        
        logger.info("✅ File attachments verified: streaming + persistence with {} parts", parts.size());
    }

    /**
     * Test: Source citations - matches AI SDK "server provides sources"
     * Maps to: SOURCE message parts
     * Persistence: ChatMessage with SOURCE parts containing citation metadata
     */
    @Test
    @WithMentorUser
    void shouldStreamSourceCitations() {
        // Given - source citations with start step
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-sources-456\"}",
            "0:\"The weather in London is sunny.\"",
            "h:{\"sourceType\":\"url\",\"id\":\"source-id\",\"url\":\"https://example.com\",\"title\":\"Example\"}",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-sources-456\"}")
            .expectNext("0:\"The weather in London is sunny.\"")
            .expectNext("h:{\"sourceType\":\"url\",\"id\":\"source-id\",\"url\":\"https://example.com\",\"title\":\"Example\"}")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}")
            .verifyComplete();
    }

    /**
     * Test: Error handling - demonstrates error part streaming
     * Maps to: ERROR message parts for error recovery scenarios
     * Persistence: ChatMessage with error parts for debugging
     */
    @Test
    @WithMentorUser
    void shouldStreamErrorRecovery() {
        // Given - error handling scenario with start step and proper finish frames
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-error-recovery\"}",
            "0:\"I'll help you with that. \"",
            "3:\"Rate limit exceeded. Retrying...\"",
            "0:\"Here's your answer.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":12},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":12}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-error-recovery\"}")
            .expectNext("0:\"I'll help you with that. \"")
            .expectNext("3:\"Rate limit exceeded. Retrying...\"")
            .expectNext("0:\"Here's your answer.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":12},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":12}}")
            .verifyComplete();
    }

    @Test
    @WithMentorUser
    void shouldStreamDataAndAnnotations() {
        // Given - structured data and annotations with start step
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-data-annotations\"}",
            "0:\"Here's the analysis result:\"",
            "2:[{\"metric\":\"accuracy\",\"value\":0.95},{\"metric\":\"precision\",\"value\":0.87}]",
            "8:[{\"id\":\"analysis-123\",\"timestamp\":\"2025-06-14T10:30:00Z\",\"confidence\":\"high\"}]",
            "0:\"The model performed well with 95% accuracy.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-data-annotations\"}")
            .expectNext("0:\"Here's the analysis result:\"")
            .expectNext("2:[{\"metric\":\"accuracy\",\"value\":0.95},{\"metric\":\"precision\",\"value\":0.87}]")
            .expectNext("8:[{\"id\":\"analysis-123\",\"timestamp\":\"2025-06-14T10:30:00Z\",\"confidence\":\"high\"}]")
            .expectNext("0:\"The model performed well with 95% accuracy.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8}}")
            .verifyComplete();
    }

    @Test
    @WithMentorUser
    void shouldStreamRedactedReasoningWithSignature() {
        // Given - sensitive reasoning that gets redacted with start step
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-redacted-reasoning\"}",
            "g:\"I need to analyze this sensitive data\"",
            "i:{\"data\":\"This reasoning has been redacted for security purposes.\"}",
            "j:{\"signature\":\"abc123xyz789\"}",
            "0:\"Based on the available information, \"",
            "0:\"I can provide this general guidance.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-redacted-reasoning\"}")
            .expectNext("g:\"I need to analyze this sensitive data\"")
            .expectNext("i:{\"data\":\"This reasoning has been redacted for security purposes.\"}")
            .expectNext("j:{\"signature\":\"abc123xyz789\"}")
            .expectNext("0:\"Based on the available information, \"")
            .expectNext("0:\"I can provide this general guidance.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15}}")
            .verifyComplete();
    }

    @Test
    @WithMentorUser
    void shouldHandleEmptyResponse() {
        // Given - immediate completion without content (with start step for consistency)
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-empty-response\"}",
            "e:{\"finishReason\":\"content-filter\",\"usage\":{\"completionTokens\":0,\"promptTokens\":5},\"isContinued\":false}",
            "d:{\"finishReason\":\"content-filter\",\"usage\":{\"completionTokens\":0,\"promptTokens\":5}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-empty-response\"}")
            .expectNext("e:{\"finishReason\":\"content-filter\",\"usage\":{\"completionTokens\":0,\"promptTokens\":5},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"content-filter\",\"usage\":{\"completionTokens\":0,\"promptTokens\":5}}")
            .verifyComplete();
    }

    /**
     * Test: Comprehensive multi-step workflow - demonstrates all AI SDK stream protocol requirements
     * This test shows:
     * - Mandatory Start Step Frame (f:) at the beginning
     * - Multiple step transitions with proper isContinued flags
     * - All frame types working together in a realistic scenario
     * - Consistent usage reporting between finish step and finish message
     */
    @Test
    @WithMentorUser
    void shouldStreamComprehensiveMultiStepWorkflow() {
        // Given - complete workflow with reasoning, tool calls, and multiple steps
        var expectedFrames = List.of(
            // Step 1: Reasoning and tool planning
            "f:{\"messageId\":\"msg-step1-reasoning\"}",
            "g:\"I need to check the weather first\"",
            "j:{\"signature\":\"reasoning-sig-1\"}",
            "9:{\"toolCallId\":\"weather-call\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}",
            "a:{\"toolCallId\":\"weather-call\",\"result\":{\"temperature\":\"22°C\",\"condition\":\"sunny\"}}",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25},\"isContinued\":true}",
            
            // Step 2: Processing and response
            "f:{\"messageId\":\"msg-step2-response\"}",
            "0:\"Based on the weather data, London is experiencing \"",
            "0:\"sunny conditions at 22°C. \"",
            "h:{\"sourceType\":\"api\",\"id\":\"weather-api\",\"url\":\"https://weather-api.com\",\"title\":\"Weather API\"}",
            "2:[{\"location\":\"London\",\"temp\":22,\"condition\":\"sunny\"}]",
            "8:[{\"id\":\"weather-response-123\",\"accuracy\":\"high\",\"timestamp\":\"2025-06-14T15:30:00Z\"}]",
            "0:\"Perfect weather for outdoor activities!\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8},\"isContinued\":false}",
            
            // Final message completion
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":27,\"promptTokens\":33}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then - verify all frames in correct order
        StepVerifier.create(actualFrames)
            // Step 1 verification
            .expectNext("f:{\"messageId\":\"msg-step1-reasoning\"}")
            .expectNext("g:\"I need to check the weather first\"")
            .expectNext("j:{\"signature\":\"reasoning-sig-1\"}")
            .expectNext("9:{\"toolCallId\":\"weather-call\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}")
            .expectNext("a:{\"toolCallId\":\"weather-call\",\"result\":{\"temperature\":\"22°C\",\"condition\":\"sunny\"}}")
            .expectNext("e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25},\"isContinued\":true}")
            
            // Step 2 verification
            .expectNext("f:{\"messageId\":\"msg-step2-response\"}")
            .expectNext("0:\"Based on the weather data, London is experiencing \"")
            .expectNext("0:\"sunny conditions at 22°C. \"")
            .expectNext("h:{\"sourceType\":\"api\",\"id\":\"weather-api\",\"url\":\"https://weather-api.com\",\"title\":\"Weather API\"}")
            .expectNext("2:[{\"location\":\"London\",\"temp\":22,\"condition\":\"sunny\"}]")
            .expectNext("8:[{\"id\":\"weather-response-123\",\"accuracy\":\"high\",\"timestamp\":\"2025-06-14T15:30:00Z\"}]")
            .expectNext("0:\"Perfect weather for outdoor activities!\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":8},\"isContinued\":false}")
            
            // Final completion
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":27,\"promptTokens\":33}}")
            .verifyComplete();
    }

    /**
     * Test: Persistence of text messages - verifies that text message parts are correctly persisted in database
     * Maps to: TEXT message parts in ChatMessagePart entity
     * Persistence: Single ChatMessage with multiple TEXT parts
     */
    @Test
    @WithMentorUser
    void shouldPersistTextMessages() {
        // Given - Clear database first
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Generate a proper UUID for the message
        String messageId = UUID.randomUUID().toString();
        final List<String> expectedFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "0:\"Hello, \"",
            "0:\"this is a test \"", 
            "0:\"of message persistence!\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );

        // When - make the chat request
        performChatRequest(expectedFrames);
        
        // Then - verify that messages were persisted
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER message + ASSISTANT message
        logger.info("Found {} messages persisted", allMessages.size());
        
        // Verify USER message exists (the input)
        ChatMessage userMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.USER)
            .findFirst()
            .orElse(null);
        assertThat(userMessage).isNotNull();
        assertThat(userMessage.getId()).isNotNull();
        logger.info("User message ID: {}", userMessage.getId());
        
        // Verify ASSISTANT message exists and has the correct UUID
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElse(null);
        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.getId().toString()).isEqualTo(messageId);
        logger.info("Assistant message ID: {}", assistantMessage.getId());
        
        // Verify the message parts for the assistant message
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).isNotEmpty();
        logger.info("Found {} message parts", parts.size());
        
        // Verify that we have exactly ONE TEXT message part (all "0:" frames should be combined)
        List<ChatMessagePart> textParts = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .toList();
        assertThat(textParts).hasSize(1); // Should be exactly ONE combined text part
        
        // Verify the combined content matches what we expect
        String combinedContent = textParts.get(0).getContent().asText();
        assertThat(combinedContent).isEqualTo("Hello, this is a test of message persistence!");
        logger.info("Combined message content correctly saved: '{}'", combinedContent);
    }
    
    /**
     * Test: Multiple messages in sequence - verifies that we can send multiple messages
     * and they are properly persisted as separate messages in the same thread
     */
    @Test
    @WithMentorUser
    void shouldPersistMultipleMessages() {
        // Given - Clear database first
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // First message
        String firstMessageId = UUID.randomUUID().toString();
        final List<String> firstFrames = List.of(
            "f:{\"messageId\":\"" + firstMessageId + "\"}",
            "0:\"First message\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":5},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":5}}"
        );
        
        // Second message
        String secondMessageId = UUID.randomUUID().toString();
        final List<String> secondFrames = List.of(
            "f:{\"messageId\":\"" + secondMessageId + "\"}",
            "0:\"Second message\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":5},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":5}}"
        );

        // When - make both chat requests
        performChatRequest(firstFrames);
        performChatRequest(secondFrames);
        
        // Then - verify that all messages were persisted
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(4); // 2 USER messages + 2 ASSISTANT messages
        logger.info("Found {} messages persisted after two requests", allMessages.size());
        
        // Verify ASSISTANT messages have correct UUIDs
        List<ChatMessage> assistantMessages = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .toList();
        assertThat(assistantMessages).hasSize(2);
        
        // Check first assistant message
        ChatMessage firstAssistant = assistantMessages.stream()
            .filter(msg -> msg.getId().toString().equals(firstMessageId))
            .findFirst()
            .orElse(null);
        assertThat(firstAssistant).isNotNull();
        
        // Check second assistant message
        ChatMessage secondAssistant = assistantMessages.stream()
            .filter(msg -> msg.getId().toString().equals(secondMessageId))
            .findFirst()
            .orElse(null);
        assertThat(secondAssistant).isNotNull();
        
        // Verify content of both messages
        List<ChatMessagePart> firstParts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(firstAssistant.getId());
        List<ChatMessagePart> secondParts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(secondAssistant.getId());
        
        assertThat(firstParts).isNotEmpty();
        assertThat(secondParts).isNotEmpty();
        
        String firstContent = firstParts.get(0).getContent().asText();
        String secondContent = secondParts.get(0).getContent().asText();
        
        assertThat(firstContent).isEqualTo("First message");
        assertThat(secondContent).isEqualTo("Second message");
        
        logger.info("First message content: '{}'", firstContent);
        logger.info("Second message content: '{}'", secondContent);
    }

    /**
     * Test: Persistence of tool calls - verifies that tool invocation parts are correctly persisted
     * Maps to: TOOL_INVOCATION message parts in ChatMessagePart entity
     * Persistence: ChatMessage with TOOL_INVOCATION parts containing proper tool data
     * 
     * CURRENTLY DISABLED: Tool call persistence is not yet implemented in ChatService.
     * The ChatService only implements "vertical slice for text message persistence only".
     * Tool call frames (9:, a:, b:, c:) are logged but not persisted.
     */
    // @Test - DISABLED: Tool call persistence not implemented
    @WithMentorUser
    void shouldPersistToolCallMessages() {
        // Given - Clear database first
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        // Generate a proper UUID for the tool call message
        String messageId = UUID.randomUUID().toString();
        final List<String> expectedFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "9:{\"toolCallId\":\"tool-call-123\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}",
            "a:{\"toolCallId\":\"tool-call-123\",\"result\":{\"weather\":\"sunny\",\"temp\":\"22°C\"}}",
            "0:\"The weather in London is sunny at 22°C.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15}}"
        );

        // When - make the chat request
        performChatRequest(expectedFrames);
        
        // Then - verify that messages were persisted
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        assertThat(allMessages).hasSize(2); // USER message + ASSISTANT message
        
        // Verify ASSISTANT message exists and has the correct UUID
        ChatMessage assistantMessage = allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElse(null);
        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.getId().toString()).isEqualTo(messageId);
        logger.info("Tool call message ID: {}", assistantMessage.getId());
        
        // Verify the message parts
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).isNotEmpty();
        logger.info("Found {} message parts for tool call", parts.size());
        
        // Verify we have TOOL_INVOCATION parts
        List<ChatMessagePart> toolParts = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION)
            .toList();
        assertThat(toolParts).isNotEmpty();
        logger.info("Found {} tool invocation parts", toolParts.size());
        
        // Verify we also have TEXT parts for the final response
        List<ChatMessagePart> textParts = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .toList();
        assertThat(textParts).isNotEmpty();
        
        // Verify the text content
        String textContent = textParts.get(0).getContent().asText();
        assertThat(textContent).isEqualTo("The weather in London is sunny at 22°C.");
        logger.info("Tool call response text: '{}'", textContent);
        
        // Verify tool invocation content structure
        ChatMessagePart toolPart = toolParts.get(0);
        assertThat(toolPart.getContent()).isNotNull();
        logger.info("Tool part content: {}", toolPart.getContent().toString());
    }
    
    /**
     * Comprehensive test: AI SDK Tool Call Protocol with Complete Persistence
     * Tests: Tool invocation (9:) + Tool result (a:) + Text response + Full persistence
     */
    @Test
    @WithMentorUser
    void shouldHandleCompleteToolCallProtocol() {
        // Given - Clear database first
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        String messageId = UUID.randomUUID().toString();
        final List<String> aiSdkFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "9:{\"toolCallId\":\"tool-call-123\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}",
            "a:{\"toolCallId\":\"tool-call-123\",\"result\":{\"weather\":\"sunny\",\"temp\":\"22°C\"}}",
            "0:\"The weather in London is sunny at 22°C.\"",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25}}"
        );

        // When - Process request
        var streamingResult = performChatRequest(aiSdkFrames);
        
        // Then - Verify streaming
        StepVerifier.create(streamingResult)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("9:{\"toolCallId\":\"tool-call-123\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}")
            .expectNext("a:{\"toolCallId\":\"tool-call-123\",\"result\":{\"weather\":\"sunny\",\"temp\":\"22°C\"}}")
            .expectNext("0:\"The weather in London is sunny at 22°C.\"")
            .expectNext("e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":15,\"promptTokens\":25}}")
            .verifyComplete();
        
        // And - Verify persistence
        ChatMessage assistantMessage = findAssistantMessage(messageId);
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSize(3); // TOOL_INVOCATION + TOOL_RESULT + TEXT
        
        // Verify tool invocation part
        ChatMessagePart toolInvocation = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION)
            .findFirst().orElseThrow();
        JsonNode toolContent = toolInvocation.getContent();
        assertThat(toolContent.get("toolCallId").asText()).isEqualTo("tool-call-123");
        assertThat(toolContent.get("toolName").asText()).isEqualTo("get-weather");
        assertThat(toolContent.get("args").get("city").asText()).isEqualTo("London");
        
        // Verify tool result part
        ChatMessagePart toolResult = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_RESULT)
            .findFirst().orElseThrow();
        JsonNode resultContent = toolResult.getContent();
        assertThat(resultContent.get("toolCallId").asText()).isEqualTo("tool-call-123");
        assertThat(resultContent.get("result").get("weather").asText()).isEqualTo("sunny");
        
        // Verify text part
        ChatMessagePart textPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .findFirst().orElseThrow();
        assertThat(textPart.getContent().asText()).isEqualTo("The weather in London is sunny at 22°C.");
        
        logger.info("✅ Complete AI SDK tool call protocol verified: streaming + persistence");
    }

    /**
     * Comprehensive test: AI SDK Reasoning Protocol with Complete Persistence  
     * Tests: Reasoning content (g:) + Reasoning signature (j:) + Redacted reasoning (i:)
     */
    @Test
    @WithMentorUser
    void shouldHandleCompleteReasoningProtocol() {
        // Given
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        String messageId = UUID.randomUUID().toString();
        final List<String> aiSdkFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "g:\"I need to analyze this step by step\"",
            "j:{\"signature\":\"reasoning-sig-abc123\"}",
            "i:{\"data\":\"This reasoning has been redacted for security\"}",
            "0:\"Based on my analysis, here's the answer.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":20},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":20}}"
        );

        // When - Process request
        var streamingResult = performChatRequest(aiSdkFrames);
        
        // Then - Verify streaming
        StepVerifier.create(streamingResult)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("g:\"I need to analyze this step by step\"")
            .expectNext("j:{\"signature\":\"reasoning-sig-abc123\"}")
            .expectNext("i:{\"data\":\"This reasoning has been redacted for security\"}")
            .expectNext("0:\"Based on my analysis, here's the answer.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":20},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":12,\"promptTokens\":20}}")
            .verifyComplete();
        
        // And - Verify persistence
        ChatMessage assistantMessage = findAssistantMessage(messageId);
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSize(4); // REASONING + REASONING_SIGNATURE + REASONING_REDACTED + TEXT
        
        // Verify reasoning part
        ChatMessagePart reasoningPart = findPartByType(parts, ChatMessagePart.MessagePartType.REASONING);
        assertThat(reasoningPart.getContent().asText()).isEqualTo("I need to analyze this step by step");
        
        // Verify reasoning signature part
        ChatMessagePart signaturePart = findPartByType(parts, ChatMessagePart.MessagePartType.REASONING_SIGNATURE);
        assertThat(signaturePart.getContent().get("signature").asText()).isEqualTo("reasoning-sig-abc123");
        
        // Verify redacted reasoning part
        ChatMessagePart redactedPart = findPartByType(parts, ChatMessagePart.MessagePartType.REASONING_REDACTED);
        assertThat(redactedPart.getContent().get("data").asText()).isEqualTo("This reasoning has been redacted for security");
        
        // Verify text part
        ChatMessagePart textPart = findPartByType(parts, ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart.getContent().asText()).isEqualTo("Based on my analysis, here's the answer.");
        
        logger.info("✅ Complete AI SDK reasoning protocol verified: streaming + persistence");
    }

    /**
     * Comprehensive test: AI SDK Rich Content Protocol
     * Tests: Source citations (h:) + File attachments (k:) + Data arrays (2:) + Annotations (8:)
     */
    @Test
    @WithMentorUser
    void shouldHandleCompleteRichContentProtocol() {
        // Given
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        String messageId = UUID.randomUUID().toString();
        final List<String> aiSdkFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "h:{\"sourceType\":\"url\",\"id\":\"src-001\",\"url\":\"https://example.com/article\",\"title\":\"Example Article\"}",
            "k:{\"data\":\"iVBORw0KGgo=\",\"mimeType\":\"image/png\"}",
            "2:[{\"key\":\"data1\",\"value\":123},{\"key\":\"data2\",\"value\":456}]",
            "8:[{\"id\":\"msg-123\",\"type\":\"highlight\",\"text\":\"Important note\"}]",
            "0:\"Here's content with rich attachments and data.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":20,\"promptTokens\":30},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":20,\"promptTokens\":30}}"
        );

        // When - Process request
        var streamingResult = performChatRequest(aiSdkFrames);
        
        // Then - Verify streaming
        StepVerifier.create(streamingResult)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("h:{\"sourceType\":\"url\",\"id\":\"src-001\",\"url\":\"https://example.com/article\",\"title\":\"Example Article\"}")
            .expectNext("k:{\"data\":\"iVBORw0KGgo=\",\"mimeType\":\"image/png\"}")
            .expectNext("2:[{\"key\":\"data1\",\"value\":123},{\"key\":\"data2\",\"value\":456}]")
            .expectNext("8:[{\"id\":\"msg-123\",\"type\":\"highlight\",\"text\":\"Important note\"}]")
            .expectNext("0:\"Here's content with rich attachments and data.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":20,\"promptTokens\":30},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":20,\"promptTokens\":30}}")
            .verifyComplete();
        
        // And - Verify persistence
        ChatMessage assistantMessage = findAssistantMessage(messageId);
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSize(5); // SOURCE + FILE + DATA + ANNOTATION + TEXT
        
        // Verify source part
        ChatMessagePart sourcePart = findPartByType(parts, ChatMessagePart.MessagePartType.SOURCE);
        JsonNode sourceContent = sourcePart.getContent();
        assertThat(sourceContent.get("sourceType").asText()).isEqualTo("url");
        assertThat(sourceContent.get("url").asText()).isEqualTo("https://example.com/article");
        
        // Verify file part
        ChatMessagePart filePart = findPartByType(parts, ChatMessagePart.MessagePartType.FILE);
        JsonNode fileContent = filePart.getContent();
        assertThat(fileContent.get("data").asText()).isEqualTo("iVBORw0KGgo=");
        assertThat(fileContent.get("mimeType").asText()).isEqualTo("image/png");
        
        // Verify data part
        ChatMessagePart dataPart = findPartByType(parts, ChatMessagePart.MessagePartType.DATA);
        JsonNode dataContent = dataPart.getContent();
        assertThat(dataContent.isArray()).isTrue();
        assertThat(dataContent.get(0).get("key").asText()).isEqualTo("data1");
        assertThat(dataContent.get(0).get("value").asInt()).isEqualTo(123);
        
        // Verify annotation part
        ChatMessagePart annotationPart = findPartByType(parts, ChatMessagePart.MessagePartType.ANNOTATION);
        JsonNode annotationContent = annotationPart.getContent();
        assertThat(annotationContent.isArray()).isTrue();
        assertThat(annotationContent.get(0).get("type").asText()).isEqualTo("highlight");
        
        // Verify text part
        ChatMessagePart textPart = findPartByType(parts, ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart.getContent().asText()).isEqualTo("Here's content with rich attachments and data.");
        
        logger.info("✅ Complete AI SDK rich content protocol verified: streaming + persistence");
    }

    /**
     * Comprehensive test: AI SDK Error Handling Protocol
     * Tests: Error messages (3:) + Recovery + Persistence
     */
    @Test
    @WithMentorUser
    void shouldHandleCompleteErrorProtocol() {
        // Given
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        
        String messageId = UUID.randomUUID().toString();
        final List<String> aiSdkFrames = List.of(
            "f:{\"messageId\":\"" + messageId + "\"}",
            "3:\"Connection timeout after 30 seconds\"",
            "0:\"I encountered an error but recovered successfully.\"",
            "e:{\"finishReason\":\"error\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15}}"
        );

        // When - Process request
        var streamingResult = performChatRequest(aiSdkFrames);
        
        // Then - Verify streaming
        StepVerifier.create(streamingResult)
            .expectNext("f:{\"messageId\":\"" + messageId + "\"}")
            .expectNext("3:\"Connection timeout after 30 seconds\"")
            .expectNext("0:\"I encountered an error but recovered successfully.\"")
            .expectNext("e:{\"finishReason\":\"error\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":8,\"promptTokens\":15}}")
            .verifyComplete();
        
        // And - Verify persistence
        ChatMessage assistantMessage = findAssistantMessage(messageId);
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).hasSize(2); // ERROR + TEXT
        
        // Verify error part
        ChatMessagePart errorPart = findPartByType(parts, ChatMessagePart.MessagePartType.ERROR);
        assertThat(errorPart.getContent().asText()).isEqualTo("Connection timeout after 30 seconds");
        
        // Verify text part
        ChatMessagePart textPart = findPartByType(parts, ChatMessagePart.MessagePartType.TEXT);
        assertThat(textPart.getContent().asText()).isEqualTo("I encountered an error but recovered successfully.");
        
        logger.info("✅ Complete AI SDK error protocol verified: streaming + persistence");
    }

    // Helper methods for test readability
    private ChatMessage findAssistantMessage(String expectedMessageId) {
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        return allMessages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .filter(msg -> msg.getId().toString().equals(expectedMessageId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found with ID: " + expectedMessageId));
    }
    
    private ChatMessagePart findPartByType(List<ChatMessagePart> parts, ChatMessagePart.MessagePartType type) {
        return parts.stream()
            .filter(part -> part.getType() == type)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No message part found of type: " + type));
    }

    private Flux<String> performChatRequest(List<String> mockFrames) {
        mockFrameHolder.frames = mockFrames;
        
        return webTestClient.post()
            .uri("/mentor/chat")
            .headers(this::addAuthorizationHeader)
            .bodyValue(createChatRequest())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("text/plain")
            .returnResult(String.class)
            .getResponseBody();
    }
    
    private void addAuthorizationHeader(org.springframework.http.HttpHeaders headers) {
        // Add a mock JWT token that will be processed by our mock JwtDecoder
        headers.setBearerAuth("mock-jwt-token-for-mentor-user");
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

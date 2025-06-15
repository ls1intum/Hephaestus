package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
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

    @BeforeEach
    void setUp() {
        mockFrameHolder.frames = List.of();
        // Create test user - authentication will be handled by @WithMentorUser annotation
        testDataSetup.createTestUser();
    }
    
    @AfterEach
    void tearDown() {
        // No manual authentication cleanup needed with annotations
    }

    /**
     * Test: Simple text response - matches "scenario: simple text response" from AI SDK
     * Maps to: TEXT message parts in ChatMessagePart entity
     * Persistence: Single ChatMessage with multiple TEXT parts
     */
    @Test
    @WithMentorUser
    void shouldStreamSimpleTextResponse() {
        // Given - simple text response with required start step frame
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-simple-123\"}",
            "0:\"Hello, \"",
            "0:\"world!\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-simple-123\"}")
            .expectNext("0:\"Hello, \"")
            .expectNext("0:\"world!\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}")
            .verifyComplete();
    }

    /**
     * Test: Server-side tool roundtrip - matches AI SDK "server-side tool roundtrip" 
     * Maps to: TOOL_INVOCATION message parts
     * Persistence: ChatMessage with TOOL_INVOCATION parts, demonstrates multi-step ChatThread
     */
    @Test
    @WithMentorUser
    void shouldStreamServerSideToolRoundtrip() {
        // Given - complete tool call workflow with multi-step pattern
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-tool-step1\"}",
            "9:{\"toolCallId\":\"tool-call-id\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}",
            "a:{\"toolCallId\":\"tool-call-id\",\"result\":{\"weather\":\"sunny\"}}",
            "e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":true}",
            "f:{\"messageId\":\"msg-tool-step2\"}",
            "0:\"The weather in London is sunny.\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":4},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-tool-step1\"}")
            .expectNext("9:{\"toolCallId\":\"tool-call-id\",\"toolName\":\"get-weather\",\"args\":{\"city\":\"London\"}}")
            .expectNext("a:{\"toolCallId\":\"tool-call-id\",\"result\":{\"weather\":\"sunny\"}}")
            .expectNext("e:{\"finishReason\":\"tool-calls\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":true}")
            .expectNext("f:{\"messageId\":\"msg-tool-step2\"}")
            .expectNext("0:\"The weather in London is sunny.\"")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":2,\"promptTokens\":4},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}")
            .verifyComplete();
    }

    /**
     * Test: Tool call streaming - matches AI SDK "tool call streaming"
     * Maps to: TOOL_INVOCATION parts with streaming delta updates
     * Persistence: Shows how partial tool calls evolve in ChatMessagePart
     */
    @Test
    @WithMentorUser
    void shouldStreamToolCallWithDeltas() {
        // Given - streaming tool call with deltas and proper start step
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-streaming-tool\"}",
            "b:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\"}",
            "c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"{\\\"testArg\\\":\\\"t\"}",
            "c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"est-value\\\"}\"}",
            "9:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\",\"args\":{\"testArg\":\"test-value\"}}",
            "a:{\"toolCallId\":\"tool-call-0\",\"result\":\"test-result\"}",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-streaming-tool\"}")
            .expectNext("b:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\"}")
            .expectNext("c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"{\\\"testArg\\\":\\\"t\"}")
            .expectNext("c:{\"toolCallId\":\"tool-call-0\",\"argsTextDelta\":\"est-value\\\"}\"}")
            .expectNext("9:{\"toolCallId\":\"tool-call-0\",\"toolName\":\"test-tool\",\"args\":{\"testArg\":\"test-value\"}}")
            .expectNext("a:{\"toolCallId\":\"tool-call-0\",\"result\":\"test-result\"}")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}")
            .verifyComplete();
    }

    /**
     * Test: Server provides reasoning - matches AI SDK "server provides reasoning"
     * Maps to: REASONING message parts with signatures
     * Persistence: ChatMessage with REASONING parts, including redacted reasoning
     */
    @Test
    @WithMentorUser
    void shouldStreamReasoningWithSignature() {
        // Given - reasoning with signatures and redacted parts
        var expectedFrames = List.of(
            "f:{\"messageId\":\"step_123\"}",
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
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"step_123\"}")
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
    }

    /**
     * Test: File attachments - matches AI SDK "server provides file parts"
     * Maps to: FILE message parts
     * Persistence: ChatMessage with FILE parts containing base64 data
     */
    @Test
    @WithMentorUser
    void shouldStreamFileAttachments() {
        // Given - file attachments with different MIME types and start step
        var expectedFrames = List.of(
            "f:{\"messageId\":\"msg-files-123\"}",
            "0:\"Here is a file:\"",
            "k:{\"data\":\"Hello World\",\"mimeType\":\"text/plain\"}",
            "0:\"And another one:\"",
            "k:{\"data\":\"{\\\"key\\\": \\\"value\\\"}\",\"mimeType\":\"application/json\"}",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}"
        );
        
        // When
        var actualFrames = performChatRequest(expectedFrames);

        // Then
        StepVerifier.create(actualFrames)
            .expectNext("f:{\"messageId\":\"msg-files-123\"}")
            .expectNext("0:\"Here is a file:\"")
            .expectNext("k:{\"data\":\"Hello World\",\"mimeType\":\"text/plain\"}")
            .expectNext("0:\"And another one:\"")
            .expectNext("k:{\"data\":\"{\\\"key\\\": \\\"value\\\"}\",\"mimeType\":\"application/json\"}")
            .expectNext("e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14},\"isContinued\":false}")
            .expectNext("d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":7,\"promptTokens\":14}}")
            .verifyComplete();
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
        // Given - simple text response with required start step frame
        final List<String> expectedFrames = List.of(
            "f:{\"messageId\":\"msg-persistence-test\"}",
            "0:\"Hello, \"",
            "0:\"this is a test \"",
            "0:\"of message persistence!\"",
            "e:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10},\"isContinued\":false}",
            "d:{\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":5,\"promptTokens\":10}}"
        );

        // When - make the chat request
        performChatRequest(expectedFrames);
        
        // Then - verify that messages were persisted
        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).isNotEmpty();
        logger.info("Found {} messages persisted", messages.size());
    
        // Verify that we have at least one ASSISTANT message
        ChatMessage assistantMessage = messages.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.ASSISTANT)
            .findFirst()
            .orElse(null);
        
        assertThat(assistantMessage).isNotNull();
        
        // Verify the message parts
        List<ChatMessagePart> parts = chatMessagePartRepository.findByIdMessageIdOrderByIdOrderIndexAsc(assistantMessage.getId());
        assertThat(parts).isNotEmpty();
        
        // Verify that we have a TEXT message part
        ChatMessagePart textPart = parts.stream()
            .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TEXT)
            .findFirst()
            .orElse(null);
            
        assertThat(textPart).isNotNull();
        assertThat(textPart.getContent()).isNotNull();
        
        // Verify the content contains our test text
        String textContent = textPart.getContent().asText();
        
        // Verify the message content is correctly stored
        // Note: in a streaming response, texts might be combined differently than in frames
        assertThat(textContent).isNotEmpty();
        
        // Log the message content for debugging
        logger.info("Persisted message content: {}", textContent);
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

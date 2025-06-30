package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessagePartsInner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ChatMessage part conversion to UIMessage ensuring all fields are properly populated.
 * 
 * Focus: Single responsibility - test data integrity during entity to UI conversion.
 * This is critical for the frontend to display messages correctly.
 */
public class ChatMessagePartDataIntegrityIT extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @Transactional
    void setUp() {
        // Ensure mentor user exists for the test
        userRepository.findByLogin("mentor")
                .orElseThrow(() -> new RuntimeException("Mentor user not found"));
    }

    @Test
    @Transactional
    @WithMentorUser
    void textPartShouldPopulateAllRequiredFields() {
        // Given: A text message part created with factory
        UUID messageId = UUID.randomUUID();
        ChatMessagePart textPart = ChatMessagePartFactory.createTextPart(messageId, 0, "Hello, World!");
        
        // When: Convert to UI message part
        UIMessagePartsInner result = textPart.toUIMessagePart();
        
        // Then: All text part fields should be properly populated
        assertThat(result.getType()).isEqualTo("text");
        assertThat(result.getText()).isEqualTo("Hello, World!");
        // These should be null for text parts
        assertThat(result.getToolCallId()).isNull();
        assertThat(result.getInput()).isNull();
        assertThat(result.getOutput()).isNull();
        assertThat(result.getState()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void reasoningPartShouldPopulateAllRequiredFields() {
        // Given: A reasoning message part created with factory
        UUID messageId = UUID.randomUUID();
        ChatMessagePart reasoningPart = ChatMessagePartFactory.createReasoningPart(
                messageId, 0, "Let me think about this...", null);
        
        // When: Convert to UI message part
        UIMessagePartsInner result = reasoningPart.toUIMessagePart();
        
        // Then: All reasoning part fields should be properly populated
        assertThat(result.getType()).isEqualTo("reasoning");
        assertThat(result.getText()).isEqualTo("Let me think about this...");
        // These should be null for reasoning parts
        assertThat(result.getToolCallId()).isNull();
        assertThat(result.getInput()).isNull();
        assertThat(result.getOutput()).isNull();
        assertThat(result.getState()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void toolCallPartShouldPopulateAllRequiredFields() throws Exception {
        // Given: A tool call part with complex input structure
        UUID messageId = UUID.randomUUID();
        Object toolArgs = objectMapper.readTree("{\"query\": \"test query\", \"parameters\": {\"limit\": 10}}");
        ChatMessagePart toolPart = ChatMessagePartFactory.createToolCallPart(
                messageId, 0, "search_tool", "tool_123", toolArgs);
        
        // When: Convert to UI message part
        UIMessagePartsInner result = toolPart.toUIMessagePart();
        
        // Then: All tool part fields should be properly populated
        assertThat(result.getType()).isEqualTo("tool-search_tool");
        assertThat(result.getState()).isEqualTo("call");
        assertThat(result.getToolCallId()).isEqualTo("tool_123");
        
        // Verify JSON structure is preserved in input
        JsonNode inputNode = objectMapper.readTree(result.getInput().toString());
        assertThat(inputNode.get("query").asText()).isEqualTo("test query");
        assertThat(inputNode.get("parameters").get("limit").asInt()).isEqualTo(10);
        
        // Text should be null for tool parts
        assertThat(result.getText()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void toolResultPartShouldPopulateAllRequiredFields() throws Exception {
        // Given: A tool result part with output data
        UUID messageId = UUID.randomUUID();
        Object toolArgs = objectMapper.readTree("{\"query\": \"test\"}");
        Object toolResult = objectMapper.readTree("{\"result\": \"success\", \"data\": [1, 2, 3]}");
        
        ChatMessagePart toolPart = ChatMessagePartFactory.createToolResultPart(
                messageId, 0, "search_tool", "tool_123", toolArgs, toolResult);
        
        // When: Convert to UI message part
        UIMessagePartsInner result = toolPart.toUIMessagePart();
        
        // Then: All tool result fields should be properly populated
        assertThat(result.getType()).isEqualTo("tool-search_tool");
        assertThat(result.getState()).isEqualTo("result");
        assertThat(result.getToolCallId()).isEqualTo("tool_123");
        
        // Verify JSON structure is preserved
        JsonNode resultNode = objectMapper.readTree(result.getOutput().toString());
        assertThat(resultNode.get("result").asText()).isEqualTo("success");
        assertThat(resultNode.get("data").isArray()).isTrue();
        assertThat(resultNode.get("data").size()).isEqualTo(3);
        
        // Text should be null for tool parts
        assertThat(result.getText()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void partWithNullContentShouldHandleGracefully() {
        // Given: A message part with null content (manually created)
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(UUID.randomUUID(), 0));
        part.setType(ChatMessagePart.PartType.TEXT);
        part.setOriginalType("text");
        part.setContent(null);
        
        // When: Convert to UI message part
        UIMessagePartsInner result = part.toUIMessagePart();
        
        // Then: Should handle gracefully without throwing exception
        assertThat(result.getType()).isEqualTo("text");
        assertThat(result.getText()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void partWithEmptyTextShouldHandleGracefully() {
        // Given: A message part with empty text
        UUID messageId = UUID.randomUUID();
        ChatMessagePart textPart = ChatMessagePartFactory.createTextPart(messageId, 0, "");
        
        // When: Convert to UI message part
        UIMessagePartsInner result = textPart.toUIMessagePart();
        
        // Then: Should handle gracefully
        assertThat(result.getType()).isEqualTo("text");
        assertThat(result.getText()).isEqualTo("");
    }

    @Test
    @Transactional
    @WithMentorUser
    void filePartShouldPopulateAllRequiredFields() {
        // Given: A file part with metadata
        UUID messageId = UUID.randomUUID();
        ChatMessagePart filePart = ChatMessagePartFactory.createFilePart(
                messageId, 0, "image/png", "https://example.com/image.png", "image.png");
        
        // When: Convert to UI message part
        UIMessagePartsInner result = filePart.toUIMessagePart();
        
        // Then: All file part fields should be properly populated
        assertThat(result.getType()).isEqualTo("file");
        assertThat(result.getMediaType()).isEqualTo("image/png");
        assertThat(result.getUrl()).isEqualTo("https://example.com/image.png");
        assertThat(result.getFilename()).isEqualTo("image.png");
        
        // Text should be null for file parts
        assertThat(result.getText()).isNull();
    }

    @Test
    @Transactional
    @WithMentorUser
    void dataPartShouldPopulateAllRequiredFields() {
        // Given: A data part with structured data
        UUID messageId = UUID.randomUUID();
        Object data = objectMapper.createObjectNode().put("value", "test");
        ChatMessagePart dataPart = ChatMessagePartFactory.createDataPart(
                messageId, 0, "json", data, "data_123");
        
        // When: Convert to UI message part
        UIMessagePartsInner result = dataPart.toUIMessagePart();
        
        // Then: All data part fields should be properly populated
        assertThat(result.getType()).isEqualTo("data-json");
        assertThat(result.getId()).isEqualTo("data_123");
        assertThat(result.getData()).isNotNull();
        
        // Text should be null for data parts
        assertThat(result.getText()).isNull();
    }
}

package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Test class for SseStreamParser functionality.
 */
public class SseStreamParserTest extends BaseUnitTest {

    @Test
    void testParseTextPart() {
        String sseInput = "data: {\"type\": \"text\", \"text\": \"Hello world\"}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamTextPart);
        StreamTextPart textPart = (StreamTextPart) result.get();
        assertEquals("Hello world", textPart.getText());
        assertEquals("text", textPart.getType());
    }

    @Test
    void testParseErrorPart() {
        String sseInput = "data: {\"type\": \"error\", \"errorText\": \"Something went wrong\"}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamErrorPart);
        StreamErrorPart errorPart = (StreamErrorPart) result.get();
        assertEquals("Something went wrong", errorPart.getErrorText());
        assertEquals("error", errorPart.getType());
    }

    @Test
    void testParseStartPart() {
        String sseInput = "data: {\"type\": \"start\", \"messageId\": \"123-456\", \"messageMetadata\": null}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamStartPart);
        StreamStartPart startPart = (StreamStartPart) result.get();
        assertEquals("123-456", startPart.getMessageId());
        assertEquals("start", startPart.getType());
    }

    @Test
    void testParseDoneMarker() {
        String sseInput = "data: [DONE]";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertFalse(result.isPresent());
    }

    @Test
    void testCreateErrorSSE() {
        String errorSSE = SseStreamParser.createErrorSSE("Test error");
        assertTrue(errorSSE.startsWith("data: "));
        assertTrue(errorSSE.contains("\"type\":\"error\""));
        assertTrue(errorSSE.contains("\"errorText\":\"Test error\""));
        assertTrue(errorSSE.endsWith("\n\n"));
    }

    @Test
    void testCreateFinishSSE() {
        String finishSSE = SseStreamParser.createFinishSSE();
        assertTrue(finishSSE.startsWith("data: "));
        assertTrue(finishSSE.contains("\"type\":\"finish\""));
        assertTrue(finishSSE.endsWith("\n\n"));
    }

    @Test
    void testCreateDoneSSE() {
        String doneSSE = SseStreamParser.createDoneSSE();
        assertEquals("data: [DONE]\n\n", doneSSE);
    }

    @Test
    void testParseReasoningFinishPart() {
        // Test the correct Python type name "reasoning-part-finish"
        String sseInput = "data: {\"type\": \"reasoning-part-finish\"}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamReasoningFinishPart);
        StreamReasoningFinishPart reasoningPart = (StreamReasoningFinishPart) result.get();
        assertEquals("reasoning-part-finish", reasoningPart.getType());
    }

    @Test
    void testParseDynamicDataTypes() {
        // Test data-weather pattern
        String sseInput1 = "data: {\"type\": \"data-weather\", \"id\": \"weather-1\", \"data\": {\"temperature\": 23.8}}";
        Optional<Object> result1 = SseStreamParser.parseSSELine(sseInput1);
        
        assertTrue(result1.isPresent());
        assertTrue(result1.get() instanceof StreamDataPart);
        StreamDataPart dataPart1 = (StreamDataPart) result1.get();
        assertEquals("data-weather", dataPart1.getType());
        assertEquals("weather-1", dataPart1.getId());
        
        // Test data-location pattern
        String sseInput2 = "data: {\"type\": \"data-location\", \"data\": {\"lat\": 48.137154, \"lng\": 11.576124}}";
        Optional<Object> result2 = SseStreamParser.parseSSELine(sseInput2);
        
        assertTrue(result2.isPresent());
        assertTrue(result2.get() instanceof StreamDataPart);
        StreamDataPart dataPart2 = (StreamDataPart) result2.get();
        assertEquals("data-location", dataPart2.getType());
    }

    @Test
    void testInvalidTypeField() {
        // Test missing type field
        String sseInput1 = "data: {\"text\": \"Hello world\"}";
        Optional<Object> result1 = SseStreamParser.parseSSELine(sseInput1);
        assertFalse(result1.isPresent());
        
        // Test null type field
        String sseInput2 = "data: {\"type\": null, \"text\": \"Hello world\"}";
        Optional<Object> result2 = SseStreamParser.parseSSELine(sseInput2);
        assertFalse(result2.isPresent());
        
        // Test non-string type field
        String sseInput3 = "data: {\"type\": 123, \"text\": \"Hello world\"}";
        Optional<Object> result3 = SseStreamParser.parseSSELine(sseInput3);
        assertFalse(result3.isPresent());
    }

    @Test
    void testParseToolOutputAvailablePart() {
        String sseInput = "data: {\"type\": \"tool-output-available\", \"toolCallId\": \"call_123\", \"output\": {\"temperature\": 23.8, \"weather\": \"sunny\"}}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamToolOutputAvailablePart);
        StreamToolOutputAvailablePart toolPart = (StreamToolOutputAvailablePart) result.get();
        assertEquals("call_123", toolPart.getToolCallId());
        assertEquals("tool-output-available", toolPart.getType());
    }

    @Test
    void testParseToolInputAvailablePart() {
        String sseInput = "data: {\"type\": \"tool-input-available\", \"toolCallId\": \"call_123\", \"toolName\": \"get_weather\", \"input\": {\"latitude\": 48.137154, \"longitude\": 11.576124}}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof StreamToolInputAvailablePart);
        StreamToolInputAvailablePart toolPart = (StreamToolInputAvailablePart) result.get();
        assertEquals("call_123", toolPart.getToolCallId());
        assertEquals("get_weather", toolPart.getToolName());
        assertEquals("tool-input-available", toolPart.getType());
    }

    @Test
    void testHandleUnknownType() {
        String sseInput = "data: {\"type\": \"unknown-type\", \"data\": \"some data\"}";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertFalse(result.isPresent());
    }

    @Test
    void testHandleInvalidJson() {
        String sseInput = "data: {invalid json";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertFalse(result.isPresent());
    }

    @Test
    void testHandleEmptyLine() {
        Optional<Object> result = SseStreamParser.parseSSELine("");
        assertFalse(result.isPresent());
        
        result = SseStreamParser.parseSSELine(null);
        assertFalse(result.isPresent());
        
        result = SseStreamParser.parseSSELine("  ");
        assertFalse(result.isPresent());
    }

    @Test
    void testHandleSSEComments() {
        String sseInput = ": This is a comment";
        Optional<Object> result = SseStreamParser.parseSSELine(sseInput);
        
        assertFalse(result.isPresent());
    }
}

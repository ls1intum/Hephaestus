package de.tum.in.www1.hephaestus.mentor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Test class for stream part processing capabilities.
 */
public class StreamPartProcessorTest extends BaseUnitTest {

    @Mock
    private IntelligenceServiceWebClient webClient;

    @Test
    void testStreamPartCallbacks() {
        // Simulate SSE data
        String sseData1 = "data: {\"type\": \"start\", \"messageId\": \"msg-123\"}\n\n";
        String sseData2 = "data: {\"type\": \"text\", \"text\": \"Hello\"}\n\n";
        String sseData3 = "data: {\"type\": \"text\", \"text\": \" world\"}\n\n";
        String sseData4 = "data: {\"type\": \"finish\"}\n\n";
        String sseData5 = "data: [DONE]\n\n";

        when(webClient.streamChat(any(ChatRequest.class), any(StreamPartProcessor.class))).thenReturn(
            Flux.just(sseData1, sseData2, sseData3, sseData4, sseData5)
        );

        // Create a test processor to capture callbacks
        TestStreamPartProcessor testProcessor = new TestStreamPartProcessor();

        ChatRequest request = new ChatRequest();

        // Test the stream with processor
        StepVerifier.create(webClient.streamChat(request, testProcessor))
            .expectNext(sseData1)
            .expectNext(sseData2)
            .expectNext(sseData3)
            .expectNext(sseData4)
            .expectNext(sseData5)
            .verifyComplete();

        // Verify callbacks were called correctly
        assertEquals(1, testProcessor.startEvents.size());
        assertEquals(2, testProcessor.textEvents.size());
        assertEquals(1, testProcessor.finishEvents.size());

        assertEquals("msg-123", testProcessor.startEvents.get(0).getMessageId());
        assertEquals("Hello", testProcessor.textEvents.get(0).getText());
        assertEquals(" world", testProcessor.textEvents.get(1).getText());
    }

    @Test
    void testToolCallbacks() {
        String toolStartData =
            "data: {\"type\": \"tool-input-start\", \"toolCallId\": \"call_123\", \"toolName\": \"get_weather\"}\n\n";
        String toolInputData =
            "data: {\"type\": \"tool-input-available\", \"toolCallId\": \"call_123\", \"toolName\": \"get_weather\", \"input\": {\"location\": \"Munich\"}}\n\n";
        String toolOutputData =
            "data: {\"type\": \"tool-output-available\", \"toolCallId\": \"call_123\", \"output\": {\"temperature\": 23.8}}\n\n";

        when(webClient.streamChat(any(ChatRequest.class), any(StreamPartProcessor.class))).thenReturn(
            Flux.just(toolStartData, toolInputData, toolOutputData)
        );

        TestStreamPartProcessor testProcessor = new TestStreamPartProcessor();
        ChatRequest request = new ChatRequest();

        StepVerifier.create(webClient.streamChat(request, testProcessor))
            .expectNext(toolStartData)
            .expectNext(toolInputData)
            .expectNext(toolOutputData)
            .verifyComplete();

        assertEquals(1, testProcessor.toolStartEvents.size());
        assertEquals(1, testProcessor.toolInputEvents.size());
        assertEquals(1, testProcessor.toolOutputEvents.size());

        assertEquals("call_123", testProcessor.toolStartEvents.get(0).getToolCallId());
        assertEquals("get_weather", testProcessor.toolStartEvents.get(0).getToolName());
    }

    @Test
    void testComprehensiveStreamPartHandling() {
        // Test various stream part types
        String reasoningData = "data: {\"type\": \"reasoning\", \"text\": \"Let me think about this...\"}\n\n";
        String reasoningFinishData = "data: {\"type\": \"reasoning-part-finish\"}\n\n";
        String stepStartData = "data: {\"type\": \"start-step\"}\n\n";
        String stepFinishData = "data: {\"type\": \"finish-step\"}\n\n";
        String sourceUrlData =
            "data: {\"type\": \"source-url\", \"sourceId\": \"src_123\", \"url\": \"https://example.com\", \"title\": \"Example\"}\n\n";
        String sourceDocData =
            "data: {\"type\": \"source-document\", \"sourceId\": \"doc_456\", \"mediaType\": \"application/pdf\", \"title\": \"Document Title\"}\n\n";
        String fileData =
            "data: {\"type\": \"file\", \"url\": \"https://example.com/file.png\", \"mediaType\": \"image/png\"}\n\n";
        String metadataData =
            "data: {\"type\": \"message-metadata\", \"messageMetadata\": {\"usage\": {\"tokens\": 150}}}\n\n";

        when(webClient.streamChat(any(ChatRequest.class), any(StreamPartProcessor.class))).thenReturn(
            Flux.just(
                reasoningData,
                reasoningFinishData,
                stepStartData,
                stepFinishData,
                sourceUrlData,
                sourceDocData,
                fileData,
                metadataData
            )
        );

        TestStreamPartProcessor testProcessor = new TestStreamPartProcessor();
        ChatRequest request = new ChatRequest();

        StepVerifier.create(webClient.streamChat(request, testProcessor))
            .expectNext(reasoningData)
            .expectNext(reasoningFinishData)
            .expectNext(stepStartData)
            .expectNext(stepFinishData)
            .expectNext(sourceUrlData)
            .expectNext(sourceDocData)
            .expectNext(fileData)
            .expectNext(metadataData)
            .verifyComplete();

        // Verify all callbacks were triggered
        assertEquals(1, testProcessor.reasoningEvents.size());
        assertEquals(1, testProcessor.reasoningFinishEvents.size());
        assertEquals(1, testProcessor.stepStartEvents.size());
        assertEquals(1, testProcessor.stepFinishEvents.size());
        assertEquals(1, testProcessor.sourceUrlEvents.size());
        assertEquals(1, testProcessor.sourceDocumentEvents.size());
        assertEquals(1, testProcessor.fileEvents.size());
        assertEquals(1, testProcessor.messageMetadataEvents.size());

        // Verify specific content
        assertEquals("Let me think about this...", testProcessor.reasoningEvents.get(0).getText());
        assertEquals("src_123", testProcessor.sourceUrlEvents.get(0).getSourceId());
        assertEquals("https://example.com", testProcessor.sourceUrlEvents.get(0).getUrl());
        assertEquals("doc_456", testProcessor.sourceDocumentEvents.get(0).getSourceId());
        assertEquals("application/pdf", testProcessor.sourceDocumentEvents.get(0).getMediaType());
    }

    @Test
    void testToolInputDeltaCallback() {
        String toolDeltaData =
            "data: {\"type\": \"tool-input-delta\", \"toolCallId\": \"call_123\", \"inputTextDelta\": \"Munich\"}\n\n";

        when(webClient.streamChat(any(ChatRequest.class), any(StreamPartProcessor.class))).thenReturn(
            Flux.just(toolDeltaData)
        );

        TestStreamPartProcessor testProcessor = new TestStreamPartProcessor();
        ChatRequest request = new ChatRequest();

        StepVerifier.create(webClient.streamChat(request, testProcessor)).expectNext(toolDeltaData).verifyComplete();

        assertEquals(1, testProcessor.toolDeltaEvents.size());
        assertEquals("call_123", testProcessor.toolDeltaEvents.get(0).getToolCallId());
        assertEquals("Munich", testProcessor.toolDeltaEvents.get(0).getInputTextDelta());
    }

    /**
     * Test implementation of StreamPartProcessor for capturing events.
     */
    static class TestStreamPartProcessor implements StreamPartProcessor {

        List<StreamStartPart> startEvents = new ArrayList<>();
        List<StreamTextPart> textEvents = new ArrayList<>();
        List<StreamFinishPart> finishEvents = new ArrayList<>();
        List<StreamToolInputStartPart> toolStartEvents = new ArrayList<>();
        List<StreamToolInputDeltaPart> toolDeltaEvents = new ArrayList<>();
        List<StreamToolInputAvailablePart> toolInputEvents = new ArrayList<>();
        List<StreamToolOutputAvailablePart> toolOutputEvents = new ArrayList<>();
        List<StreamReasoningPart> reasoningEvents = new ArrayList<>();
        List<StreamReasoningFinishPart> reasoningFinishEvents = new ArrayList<>();
        List<StreamSourceUrlPart> sourceUrlEvents = new ArrayList<>();
        List<StreamSourceDocumentPart> sourceDocumentEvents = new ArrayList<>();
        List<StreamFilePart> fileEvents = new ArrayList<>();
        List<StreamDataPart> dataEvents = new ArrayList<>();
        List<StreamStepStartPart> stepStartEvents = new ArrayList<>();
        List<StreamStepFinishPart> stepFinishEvents = new ArrayList<>();
        List<StreamMessageMetadataPart> messageMetadataEvents = new ArrayList<>();
        List<StreamErrorPart> errorEvents = new ArrayList<>();

        @Override
        public void onStreamStart(StreamStartPart startPart) {
            startEvents.add(startPart);
        }

        @Override
        public void onTextChunk(StreamTextPart textPart) {
            textEvents.add(textPart);
        }

        @Override
        public void onStreamFinish(StreamFinishPart finishPart) {
            finishEvents.add(finishPart);
        }

        @Override
        public void onToolInputStart(StreamToolInputStartPart toolStartPart) {
            toolStartEvents.add(toolStartPart);
        }

        @Override
        public void onToolInputDelta(StreamToolInputDeltaPart toolDeltaPart) {
            toolDeltaEvents.add(toolDeltaPart);
        }

        @Override
        public void onToolInputAvailable(StreamToolInputAvailablePart toolInputPart) {
            toolInputEvents.add(toolInputPart);
        }

        @Override
        public void onToolOutputAvailable(StreamToolOutputAvailablePart toolOutputPart) {
            toolOutputEvents.add(toolOutputPart);
        }

        @Override
        public void onReasoningChunk(StreamReasoningPart reasoningPart) {
            reasoningEvents.add(reasoningPart);
        }

        @Override
        public void onReasoningFinish(StreamReasoningFinishPart reasoningFinishPart) {
            reasoningFinishEvents.add(reasoningFinishPart);
        }

        @Override
        public void onSourceUrl(StreamSourceUrlPart sourceUrlPart) {
            sourceUrlEvents.add(sourceUrlPart);
        }

        @Override
        public void onSourceDocument(StreamSourceDocumentPart sourceDocumentPart) {
            sourceDocumentEvents.add(sourceDocumentPart);
        }

        @Override
        public void onFile(StreamFilePart filePart) {
            fileEvents.add(filePart);
        }

        @Override
        public void onDataPart(StreamDataPart dataPart) {
            dataEvents.add(dataPart);
        }

        @Override
        public void onStepStart(StreamStepStartPart stepStartPart) {
            stepStartEvents.add(stepStartPart);
        }

        @Override
        public void onStepFinish(StreamStepFinishPart stepFinishPart) {
            stepFinishEvents.add(stepFinishPart);
        }

        @Override
        public void onMessageMetadata(StreamMessageMetadataPart messageMetadataPart) {
            messageMetadataEvents.add(messageMetadataPart);
        }

        @Override
        public void onStreamError(StreamErrorPart errorPart) {
            errorEvents.add(errorPart);
        }
    }
}

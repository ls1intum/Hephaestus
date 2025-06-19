package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for common stream part processing logic.
 */
public class StreamPartProcessorUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamPartProcessorUtils.class);
    
    /**
     * Process an SSE chunk and trigger appropriate callbacks.
     */
    public static void processSSEChunk(String sseChunk, StreamPartProcessor processor) {
        SseStreamParser.parseSSELine(sseChunk).ifPresent(streamPart -> {
            callProcessorCallback(streamPart, processor);
        });
    }
    
    /**
     * Call appropriate processor callback based on stream part type.
     */
    public static void callProcessorCallback(Object streamPart, StreamPartProcessor processor) {
        try {
            switch (streamPart) {
                case StreamStartPart start -> processor.onStreamStart(start);
                case StreamTextPart text -> processor.onTextChunk(text);
                case StreamErrorPart error -> processor.onStreamError(error);
                case StreamFinishPart finish -> processor.onStreamFinish(finish);
                case StreamToolInputStartPart toolStart -> processor.onToolInputStart(toolStart);
                case StreamToolInputDeltaPart toolDelta -> processor.onToolInputDelta(toolDelta);
                case StreamToolInputAvailablePart toolInput -> processor.onToolInputAvailable(toolInput);
                case StreamToolOutputAvailablePart toolOutput -> processor.onToolOutputAvailable(toolOutput);
                case StreamReasoningPart reasoning -> processor.onReasoningChunk(reasoning);
                case StreamReasoningFinishPart reasoningFinish -> processor.onReasoningFinish(reasoningFinish);
                case StreamSourceUrlPart sourceUrl -> processor.onSourceUrl(sourceUrl);
                case StreamSourceDocumentPart sourceDocument -> processor.onSourceDocument(sourceDocument);
                case StreamFilePart file -> processor.onFile(file);
                case StreamDataPart data -> processor.onDataPart(data);
                case StreamStepStartPart stepStart -> processor.onStepStart(stepStart);
                case StreamStepFinishPart stepFinish -> processor.onStepFinish(stepFinish);
                case StreamMessageMetadataPart messageMetadata -> processor.onMessageMetadata(messageMetadata);
                default -> processor.onUnknownStreamPart(streamPart);
            }
        } catch (Exception e) {
            logger.error("Error in stream part processor callback for {}: {}", 
                        streamPart.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}

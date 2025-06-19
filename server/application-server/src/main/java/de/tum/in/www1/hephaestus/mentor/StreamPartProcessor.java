package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;

/**
 * Callback interface for processing stream parts during chat streaming.
 * Provides hooks for persistence, monitoring, and business logic.
 */
public interface StreamPartProcessor {
    /**
     * Called when a stream starts.
     * @param startPart The start stream part
     */
    default void onStreamStart(StreamStartPart startPart) {}

    /**
     * Called for each text chunk.
     * @param textPart The text stream part
     */
    default void onTextChunk(StreamTextPart textPart) {}

    /**
     * Called when a tool input starts.
     * @param toolStartPart The tool input start part
     */
    default void onToolInputStart(StreamToolInputStartPart toolStartPart) {}

    /**
     * Called when tool input delta is received.
     * @param toolDeltaPart The tool input delta part
     */
    default void onToolInputDelta(StreamToolInputDeltaPart toolDeltaPart) {}

    /**
     * Called when tool input is available.
     * @param toolInputPart The tool input available part
     */
    default void onToolInputAvailable(StreamToolInputAvailablePart toolInputPart) {}

    /**
     * Called when tool output is available.
     * @param toolOutputPart The tool output available part
     */
    default void onToolOutputAvailable(StreamToolOutputAvailablePart toolOutputPart) {}

    /**
     * Called for reasoning chunks.
     * @param reasoningPart The reasoning stream part
     */
    default void onReasoningChunk(StreamReasoningPart reasoningPart) {}

    /**
     * Called when reasoning finishes.
     * @param reasoningFinishPart The reasoning finish part
     */
    default void onReasoningFinish(StreamReasoningFinishPart reasoningFinishPart) {}

    /**
     * Called for source URL parts.
     * @param sourceUrlPart The source URL part
     */
    default void onSourceUrl(StreamSourceUrlPart sourceUrlPart) {}

    /**
     * Called for source document parts.
     * @param sourceDocumentPart The source document part
     */
    default void onSourceDocument(StreamSourceDocumentPart sourceDocumentPart) {}

    /**
     * Called for file parts.
     * @param filePart The file part
     */
    default void onFile(StreamFilePart filePart) {}

    /**
     * Called for data parts.
     * @param dataPart The data stream part
     */
    default void onDataPart(StreamDataPart dataPart) {}

    /**
     * Called when a step starts.
     * @param stepStartPart The step start part
     */
    default void onStepStart(StreamStepStartPart stepStartPart) {}

    /**
     * Called when a step finishes.
     * @param stepFinishPart The step finish part
     */
    default void onStepFinish(StreamStepFinishPart stepFinishPart) {}

    /**
     * Called for message metadata parts.
     * @param messageMetadataPart The message metadata part
     */
    default void onMessageMetadata(StreamMessageMetadataPart messageMetadataPart) {}

    /**
     * Called when an error occurs in the stream.
     * @param errorPart The error stream part
     */
    default void onStreamError(StreamErrorPart errorPart) {}

    /**
     * Called when the stream finishes.
     * @param finishPart The finish stream part
     */
    default void onStreamFinish(StreamFinishPart finishPart) {}

    /**
     * Called for any unhandled stream part types.
     * @param streamPart The unknown stream part
     */
    default void onUnknownStreamPart(Object streamPart) {}
}

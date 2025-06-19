package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example implementation of StreamPartProcessor for persistence.
 * This demonstrates how to use the callback system for real-time stream processing.
 */
@Component
public class PersistenceStreamPartProcessor implements StreamPartProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(PersistenceStreamPartProcessor.class);
    
    private String currentMessageId;
    private StringBuilder currentMessageContent = new StringBuilder();
    
    @Override
    public void onStreamStart(StreamStartPart startPart) {
        this.currentMessageId = startPart.getMessageId();
        this.currentMessageContent = new StringBuilder();
        
        logger.debug("Started new message stream: messageId={}", currentMessageId);
        
        // TODO: Create ChatMessage entity in database
        // Example: chatMessageRepository.createMessage(currentMessageId, startPart.getMessageMetadata());
    }
    
    @Override
    public void onTextChunk(StreamTextPart textPart) {
        currentMessageContent.append(textPart.getText());
        
        logger.trace("Accumulated text content: length={}", currentMessageContent.length());
        
        // TODO: Optionally persist text chunks for real-time updates
        // Example: chatMessageRepository.appendTextChunk(currentMessageId, textPart.getText());
    }
    
    @Override
    public void onToolInputStart(StreamToolInputStartPart toolStartPart) {
        logger.debug("Tool execution started: toolName={}, callId={}", 
                   toolStartPart.getToolName(), toolStartPart.getToolCallId());
        
        // TODO: Create tool execution record
        // Example: toolExecutionRepository.createExecution(
        //     currentMessageId, 
        //     toolStartPart.getToolCallId(), 
        //     toolStartPart.getToolName()
        // );
    }
    
    @Override
    public void onToolInputDelta(StreamToolInputDeltaPart toolDeltaPart) {
        logger.trace("Tool input delta: callId={}, delta={}", 
                   toolDeltaPart.getToolCallId(), toolDeltaPart.getInputTextDelta());
        
        // TODO: Append tool input delta for streaming updates
        // Example: toolExecutionRepository.appendInputDelta(
        //     toolDeltaPart.getToolCallId(), 
        //     toolDeltaPart.getInputTextDelta()
        // );
    }
    
    @Override
    public void onToolInputAvailable(StreamToolInputAvailablePart toolInputPart) {
        logger.debug("Tool input available: toolName={}, callId={}", 
                   toolInputPart.getToolName(), toolInputPart.getToolCallId());
        
        // TODO: Persist tool input
        // Example: toolExecutionRepository.setInput(
        //     toolInputPart.getToolCallId(), 
        //     toolInputPart.getInput()
        // );
    }
    
    @Override
    public void onToolOutputAvailable(StreamToolOutputAvailablePart toolOutputPart) {
        logger.debug("Tool output available: callId={}", toolOutputPart.getToolCallId());
        
        // TODO: Persist tool output
        // Example: toolExecutionRepository.setOutput(
        //     toolOutputPart.getToolCallId(), 
        //     toolOutputPart.getOutput()
        // );
    }
    
    @Override
    public void onReasoningChunk(StreamReasoningPart reasoningPart) {
        logger.trace("Reasoning chunk: length={}", reasoningPart.getText().length());
        
        // TODO: Persist reasoning content separately if needed
        // Example: reasoningRepository.appendChunk(currentMessageId, reasoningPart.getText());
    }
    
    @Override
    public void onReasoningFinish(StreamReasoningFinishPart reasoningFinishPart) {
        logger.debug("Reasoning finished for message: {}", currentMessageId);
        
        // TODO: Mark reasoning as complete
        // Example: reasoningRepository.markComplete(currentMessageId);
    }
    
    @Override
    public void onSourceUrl(StreamSourceUrlPart sourceUrlPart) {
        logger.debug("Source URL: sourceId={}, url={}", sourceUrlPart.getSourceId(), sourceUrlPart.getUrl());
        
        // TODO: Persist source URL reference
        // Example: sourceRepository.addUrlSource(
        //     currentMessageId, 
        //     sourceUrlPart.getSourceId(), 
        //     sourceUrlPart.getUrl(), 
        //     sourceUrlPart.getTitle()
        // );
    }
    
    @Override
    public void onSourceDocument(StreamSourceDocumentPart sourceDocumentPart) {
        logger.debug("Source document: sourceId={}, title={}", 
                   sourceDocumentPart.getSourceId(), sourceDocumentPart.getTitle());
        
        // TODO: Persist source document reference
        // Example: sourceRepository.addDocumentSource(
        //     currentMessageId, 
        //     sourceDocumentPart.getSourceId(), 
        //     sourceDocumentPart.getMediaType(), 
        //     sourceDocumentPart.getTitle(), 
        //     sourceDocumentPart.getFilename()
        // );
    }
    
    @Override
    public void onFile(StreamFilePart filePart) {
        logger.debug("File part: mediaType={}, url={}", filePart.getMediaType(), filePart.getUrl());
        
        // TODO: Persist file reference
        // Example: fileRepository.addFile(
        //     currentMessageId, 
        //     filePart.getUrl(), 
        //     filePart.getMediaType()
        // );
    }
    
    @Override
    public void onDataPart(StreamDataPart dataPart) {
        logger.debug("Data part received: type={}, id={}", dataPart.getType(), dataPart.getId());
        
        // TODO: Persist structured data parts
        // Example: dataPartRepository.saveDataPart(
        //     currentMessageId, 
        //     dataPart.getType(), 
        //     dataPart.getId(), 
        //     dataPart.getData()
        // );
    }
    
    @Override
    public void onStepStart(StreamStepStartPart stepStartPart) {
        logger.debug("Step started for message: {}", currentMessageId);
        
        // TODO: Track step execution
        // Example: stepRepository.startStep(currentMessageId);
    }
    
    @Override
    public void onStepFinish(StreamStepFinishPart stepFinishPart) {
        logger.debug("Step finished for message: {}", currentMessageId);
        
        // TODO: Mark step as complete
        // Example: stepRepository.finishStep(currentMessageId);
    }
    
    @Override
    public void onMessageMetadata(StreamMessageMetadataPart messageMetadataPart) {
        logger.debug("Message metadata received for: {}", currentMessageId);
        
        // TODO: Update message metadata
        // Example: chatMessageRepository.updateMetadata(
        //     currentMessageId, 
        //     messageMetadataPart.getMessageMetadata()
        // );
    }
    
    @Override
    public void onStreamError(StreamErrorPart errorPart) {
        logger.error("Stream error occurred: {}", errorPart.getErrorText());
        
        // TODO: Mark message as failed and persist error
        // Example: chatMessageRepository.markAsFailed(currentMessageId, errorPart.getErrorText());
    }
    
    @Override
    public void onStreamFinish(StreamFinishPart finishPart) {
        logger.debug("Message stream completed: messageId={}, finalContentLength={}", 
                   currentMessageId, currentMessageContent.length());
        
        // TODO: Finalize message persistence
        // Example: chatMessageRepository.finalizeMessage(
        //     currentMessageId, 
        //     currentMessageContent.toString(), 
        //     finishPart.getMessageMetadata()
        // );
        
        // Clean up
        this.currentMessageId = null;
        this.currentMessageContent = new StringBuilder();
    }
    
    @Override
    public void onUnknownStreamPart(Object streamPart) {
        logger.warn("Unknown stream part type: {}", streamPart.getClass().getSimpleName());
        
        // TODO: Log unknown parts for future analysis
        // Example: unknownPartRepository.logUnknownPart(currentMessageId, streamPart);
    }
}

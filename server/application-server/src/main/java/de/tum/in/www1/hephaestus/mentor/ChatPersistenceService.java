package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting chat streams to database.
 * Thread-safe and stateless - creates session state per stream.
 */
@Service
@Transactional
public class ChatPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ChatPersistenceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatMessagePartRepository chatMessagePartRepository;

    /**
     * Creates a processor for a complete chat request, handling thread and user message persistence.
     * This method encapsulates the full setup required for a chat request.
     */
    public StreamPartProcessor createProcessorForRequest(User user, ChatRequestDTO chatRequest) {
        // Get or create chat thread
        ChatThread thread = getOrCreateChatThread(chatRequest.id(), user);

        // Persist user messages from the request and get the last one as parent
        ChatMessage parentMessage = persistUserMessages(chatRequest.messages(), thread);

        // Create processor with proper thread and parent message
        return createProcessor(user, thread, parentMessage);
    }

    /**
     * Creates a new stateful processor for a specific chat stream.
     * Each stream gets its own isolated state and database transaction context.
     */
    public StreamPartProcessor createProcessor(User user, ChatThread thread, ChatMessage parentMessage) {
        return new ChatStreamProcessor(user, thread, parentMessage);
    }

    /**
     * Stateful processor for a single chat stream.
     * Encapsulates all state and synchronization for one conversation.
     */
    private class ChatStreamProcessor implements StreamPartProcessor {

        @SuppressWarnings("unused") // Reserved for future use
        private final User user;

        private final ChatThread thread;
        private ChatMessage parentMessage;
        private final ReentrantLock lock = new ReentrantLock();

        // Stream state - isolated per processor instance
        private ChatMessage currentMessage;
        private final StringBuilder textContent = new StringBuilder();
        private final StringBuilder reasoningContent = new StringBuilder();
        private final AtomicInteger partOrder = new AtomicInteger(0);
        
        // Track the current tool part being processed for input/output combination
        private ChatMessagePart currentToolPart = null;
        private String currentToolCallId = null;

        public ChatStreamProcessor(User user, ChatThread thread, ChatMessage parentMessage) {
            this.user = user;
            this.thread = thread;
            this.parentMessage = parentMessage;
        }

        @Override
        public void onStreamStart(StreamStartPart startPart) {
            lock.lock();
            try {
                logger.debug("Starting stream for messageId={} in thread={}", startPart.getMessageId(), thread.getId());

                // If we already have a current message, update the parent for chaining
                if (currentMessage != null) {
                    parentMessage = currentMessage;
                }

                // Create assistant message
                currentMessage = new ChatMessage();
                currentMessage.setId(UUID.fromString(startPart.getMessageId()));
                currentMessage.setThread(thread);
                currentMessage.setParentMessage(parentMessage);
                currentMessage.setRole(ChatMessage.Role.ASSISTANT);

                // Persist immediately for referential integrity
                chatMessageRepository.save(currentMessage);

                // Update thread's selected leaf
                thread.setSelectedLeafMessage(currentMessage);
                chatThreadRepository.save(thread);
                
                // Reset part order for new message
                partOrder.set(0);
            } catch (Exception e) {
                logger.error("Failed to start stream: {}", e.getMessage(), e);
                throw new RuntimeException("Stream start failed", e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onTextChunk(StreamTextPart textPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received text chunk without message start");
                    return;
                }

                // Just accumulate the text, don't create parts yet
                textContent.append(textPart.getText());
            } catch (Exception e) {
                logger.error("Failed to process text chunk: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolInputStart(StreamToolInputStartPart toolInputStart) {
            // Tool input start is a flow control marker, not persisted as parts
            logger.debug("Tool input started for message: {}", currentMessage != null ? currentMessage.getId() : "null");
        }

        @Override
        public void onToolInputDelta(StreamToolInputDeltaPart toolInputDelta) {
            // Delta parts are accumulated and handled in onToolInputAvailable
            logger.debug("Tool input delta received for message: {}", currentMessage != null ? currentMessage.getId() : "null");
        }

        @Override
        public void onToolInputAvailable(StreamToolInputAvailablePart toolInput) {
            lock.lock();
            try {
                // Flush any accumulated text before creating tool part to maintain proper ordering
                flushAccumulatedText();
                
                if (currentMessage == null) {
                    logger.warn("Received tool input without message start");
                    return;
                }

                // Create a new tool part for this tool call
                ChatMessagePart toolPart = new ChatMessagePart();
                ChatMessagePartId partId = new ChatMessagePartId(
                    currentMessage.getId(),
                    partOrder.incrementAndGet()
                );
                toolPart.setId(partId);
                toolPart.setMessage(currentMessage);
                toolPart.setType(ChatMessagePart.PartType.TOOL);
                toolPart.setOriginalType("tool-" + toolInput.getToolName()); // Set originalType for getToolName() to work

                // Create content with tool call ID and input
                var toolContent = objectMapper.createObjectNode();
                toolContent.put("toolCallId", toolInput.getToolCallId());
                
                // Handle input - if it's a string, parse it; otherwise use as-is
                Object input = toolInput.getInput();
                if (input instanceof String inputStr) {
                    try {
                        JsonNode inputNode = objectMapper.readTree(inputStr);
                        toolContent.set("input", inputNode);
                    } catch (Exception e) {
                        logger.warn("Failed to parse tool input JSON, storing as string: {}", e.getMessage());
                        toolContent.put("input", inputStr);
                    }
                } else {
                    // Input is already an object, convert to JsonNode
                    toolContent.set("input", objectMapper.valueToTree(input));
                }
                
                toolPart.setContent(toolContent);

                chatMessagePartRepository.save(toolPart);
                logger.debug("Saved tool input part: messageId={}, toolCallId={}", 
                    currentMessage.getId(), toolInput.getToolCallId());
                
                // Store reference to this tool part for output update
                currentToolPart = toolPart;
                currentToolCallId = toolInput.getToolCallId();
            } catch (Exception e) {
                logger.error("Failed to process tool input: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolOutputAvailable(StreamToolOutputAvailablePart toolOutput) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received tool output without message start");
                    return;
                }

                // Update the current tool part with output if it matches the tool call ID
                if (currentToolPart != null && toolOutput.getToolCallId().equals(currentToolCallId)) {
                    var existingContent = (com.fasterxml.jackson.databind.node.ObjectNode) currentToolPart.getContent();
                    
                    // Handle output - if it's a string, parse it; otherwise use as-is
                    Object output = toolOutput.getOutput();
                    if (output instanceof String outputStr) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode outputNode = objectMapper.readTree(outputStr);
                            existingContent.set("output", outputNode);
                        } catch (Exception e) {
                            logger.warn("Failed to parse tool output JSON, storing as string: {}", e.getMessage());
                            existingContent.put("output", outputStr);
                        }
                    } else {
                        // Output is already an object, convert to JsonNode
                        existingContent.set("output", objectMapper.valueToTree(output));
                    }
                    
                    chatMessagePartRepository.save(currentToolPart);
                    logger.debug("Updated tool part with output: messageId={}, toolCallId={}", 
                        currentMessage.getId(), toolOutput.getToolCallId());
                    
                    // Clear the reference since this tool call is complete
                    currentToolPart = null;
                    currentToolCallId = null;
                } else {
                    logger.warn("Received tool output for unmatched tool call: expected={}, received={}", 
                        currentToolCallId, toolOutput.getToolCallId());
                }
            } catch (Exception e) {
                logger.error("Failed to process tool output: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolOutputError(StreamToolOutputErrorPart errorPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received tool output error without message start");
                    return;
                }

                String toolCallId = errorPart.getToolCallId();
                String errorText = errorPart.getErrorText();

                // Update the current tool part with error if it matches the tool call ID
                if (currentToolPart != null && toolCallId.equals(currentToolCallId)) {
                    var existingContent = (com.fasterxml.jackson.databind.node.ObjectNode) currentToolPart.getContent();
                    existingContent.put("errorText", errorText);
                    existingContent.put("state", "output-error");
                    // Don't add output field when there's an error
                    chatMessagePartRepository.save(currentToolPart);
                    logger.debug("Updated tool part with error: messageId={}, toolCallId={}, error={}", 
                        currentMessage.getId(), toolCallId, errorText);
                    
                    // Clear the reference since this tool call is complete (with error)
                    currentToolPart = null;
                    currentToolCallId = null;
                } else {
                    logger.warn("Received tool output error for unmatched tool call: expected={}, received={}", 
                        currentToolCallId, toolCallId);
                }
            } catch (Exception e) {
                logger.error("Failed to process tool output error: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onStreamFinish(StreamFinishPart finishPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received finish without message start");
                    return;
                }

                // Flush any remaining accumulated text
                flushAccumulatedText();

                logger.debug(
                    "Message stream completed: messageId={}",
                    currentMessage.getId()
                );

                // Final save of the message
                chatMessageRepository.save(currentMessage);
                
                // Update thread's selected leaf message
                thread.setSelectedLeafMessage(currentMessage);
                chatThreadRepository.save(thread);
                
                // Update parent for potential next assistant message
                parentMessage = currentMessage;
                
            } catch (Exception e) {
                logger.error("Failed to finish stream: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onStreamError(StreamErrorPart errorPart) {
            lock.lock();
            try {
                logger.error("Stream error occurred");

                if (currentMessage != null) {
                    createMessagePart(ChatMessagePart.PartType.TEXT, errorPart);
                }
            } catch (Exception e) {
                logger.error("Failed to process stream error: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        /**
         * Flushes accumulated text content as a TEXT part if there's any content.
         * This ensures proper part ordering when switching between content types.
         */
        private void flushAccumulatedText() {
            if (currentMessage != null && textContent.length() > 0) {
                ChatMessagePart textPart = new ChatMessagePart();
                ChatMessagePartId partId = new ChatMessagePartId(
                    currentMessage.getId(),
                    partOrder.incrementAndGet()
                );
                textPart.setId(partId);
                textPart.setMessage(currentMessage);
                textPart.setType(ChatMessagePart.PartType.TEXT);
                textPart.setContent(objectMapper.valueToTree(textContent.toString()));

                chatMessagePartRepository.save(textPart);
                logger.debug("Flushed accumulated text part: messageId={}, content={}", 
                    currentMessage.getId(), textContent.toString());
                
                // Clear the text content
                textContent.setLength(0);
            }
        }

        /**
         * Creates and persists a message part with proper sequencing.
         */
        private void createMessagePart(ChatMessagePart.PartType type, Object streamPart) {
            if (currentMessage == null) return;

            try {
                ChatMessagePart part = new ChatMessagePart();

                ChatMessagePartId partId = new ChatMessagePartId(currentMessage.getId(), partOrder.incrementAndGet());
                part.setId(partId);

                part.setMessage(currentMessage);
                part.setType(type);

                // Handle specific stream part content structure based on type
                switch (type) {
                    case TEXT -> {
                        if (streamPart instanceof StreamTextPart textPart) {
                            part.setContent(objectMapper.valueToTree(textPart.getText()));
                        } else {
                            part.setContent(objectMapper.valueToTree(streamPart));
                        }
                    }
                    case TOOL -> {
                        // Handle tool parts with input/output structure
                        if (streamPart instanceof StreamToolInputAvailablePart toolInput) {
                            var toolContent = objectMapper.createObjectNode();
                            toolContent.set("input", objectMapper.valueToTree(toolInput.getInput()));
                            part.setContent(toolContent);
                        } else if (streamPart instanceof StreamToolOutputAvailablePart toolOutput) {
                            // Find existing tool part and update with output
                            var existingToolPart = currentMessage.getParts().stream()
                                .filter(p -> p.getType() == ChatMessagePart.PartType.TOOL && 
                                    toolOutput.getToolCallId().equals(p.getContent().get("toolCallId")))
                                .findFirst();
                            
                            if (existingToolPart.isPresent()) {
                                var existingPart = existingToolPart.get();
                                var existingContent = existingPart.getContent();
                                if (existingContent.isObject()) {
                                    ((com.fasterxml.jackson.databind.node.ObjectNode) existingContent)
                                        .set("output", objectMapper.valueToTree(toolOutput.getOutput()));
                                    chatMessagePartRepository.save(existingPart);
                                    return; // Don't create a new part, just update existing
                                }
                            }
                        }
                        // Default tool handling for other tool parts
                        var toolContent = objectMapper.createObjectNode();
                        if (streamPart instanceof StreamToolInputAvailablePart toolInput) {
                            toolContent.put("toolName", toolInput.getToolName());
                            toolContent.put("toolCallId", toolInput.getToolCallId());
                            toolContent.set("input", objectMapper.valueToTree(toolInput.getInput()));
                        } else if (streamPart instanceof StreamToolOutputAvailablePart toolOutput) {
                            toolContent.put("toolCallId", toolOutput.getToolCallId());
                            toolContent.set("output", objectMapper.valueToTree(toolOutput.getOutput()));
                        }
                        part.setContent(toolContent);
                    }
                    case SOURCE_URL -> {
                        if (streamPart instanceof StreamSourceUrlPart sourceUrl) {
                            var sourceContent = objectMapper.createObjectNode();
                            sourceContent.put("url", sourceUrl.getUrl());
                            sourceContent.put("title", sourceUrl.getTitle());
                            part.setContent(sourceContent);
                        }
                    }
                    case SOURCE_DOCUMENT -> {
                        if (streamPart instanceof StreamSourceDocumentPart sourceDoc) {
                            var sourceContent = objectMapper.createObjectNode();
                            sourceContent.put("sourceId", sourceDoc.getSourceId());
                            sourceContent.put("title", sourceDoc.getTitle());
                            part.setContent(sourceContent);
                        }
                    }
                    case FILE -> {
                        if (streamPart instanceof StreamFilePart filePart) {
                            var fileContent = objectMapper.createObjectNode();
                            fileContent.put("url", filePart.getUrl());
                            fileContent.put("mediaType", filePart.getMediaType());
                            part.setContent(fileContent);
                        }
                    }
                    case DATA -> {
                        if (streamPart instanceof StreamDataPart dataPart) {
                            part.setOriginalType(dataPart.getType()); // Set originalType for data parts
                            var dataContent = objectMapper.createObjectNode();
                            dataContent.put("type", dataPart.getType());
                            
                            // Handle data - if it's a string, parse it; otherwise use as-is
                            Object data = dataPart.getData();
                            if (data instanceof String dataStr) {
                                try {
                                    com.fasterxml.jackson.databind.JsonNode dataNode = objectMapper.readTree(dataStr);
                                    dataContent.set("data", dataNode);
                                } catch (Exception e) {
                                    logger.warn("Failed to parse data JSON, storing as string: {}", e.getMessage());
                                    dataContent.put("data", dataStr);
                                }
                            } else {
                                // Data is already an object, convert to JsonNode
                                dataContent.set("data", objectMapper.valueToTree(data));
                            }
                            
                            part.setContent(dataContent);
                        }
                    }
                    default -> part.setContent(objectMapper.valueToTree(streamPart));
                }

                // Ensure the message is properly attached and persisted
                chatMessageRepository.save(currentMessage);
                chatMessagePartRepository.save(part);
                // Don't add to the collection here since we're using lazy loading
                // The parts will be loaded when needed from the database
                
                logger.debug("Saved message part: type={}, messageId={}, partId={}", 
                    type, currentMessage.getId(), part.getId());
            } catch (Exception e) {
                logger.error("Failed to create message part: {}", e.getMessage(), e);
                throw new RuntimeException("Message part persistence failed", e);
            }
        }

        // Implement remaining StreamPartProcessor methods...
        @Override
        public void onStepStart(StreamStepStartPart stepStart) {
            // Flush any accumulated text at step boundaries to maintain proper ordering
            flushAccumulatedText();
            // Step start/finish are flow control markers, not persisted as parts
            logger.debug("Step started for message: {}", currentMessage != null ? currentMessage.getId() : "null");
        }

        @Override
        public void onStepFinish(StreamStepFinishPart stepFinish) {
            // Flush any accumulated text at step boundaries to maintain proper ordering
            flushAccumulatedText();
            // Step start/finish are flow control markers, not persisted as parts
            logger.debug("Step finished for message: {}", currentMessage != null ? currentMessage.getId() : "null");
        }

        @Override
        public void onReasoningChunk(StreamReasoningPart reasoning) {
            lock.lock();
            try {
                logger.debug("Processing reasoning chunk: {}", reasoning.getText());
                if (currentMessage == null) {
                    logger.warn("Received reasoning chunk without message start");
                    return;
                }

                // Accumulate reasoning text for later creation as a single part
                reasoningContent.append(reasoning.getText());
            } catch (Exception e) {
                logger.error("Failed to process reasoning chunk: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onReasoningFinish(StreamReasoningFinishPart reasoningFinish) {
            lock.lock();
            try {
                logger.debug("Processing reasoning finish");
                if (currentMessage == null || reasoningContent.length() == 0) {
                    logger.warn("Received reasoning finish without message start or content, messageNull={}, contentLength={}", 
                        currentMessage == null, reasoningContent.length());
                    return;
                }

                // Flush any accumulated text before creating reasoning part to maintain proper ordering
                flushAccumulatedText();

                // Create a single reasoning part from accumulated content
                ChatMessagePart reasoningPart = new ChatMessagePart();
                ChatMessagePartId partId = new ChatMessagePartId(
                    currentMessage.getId(),
                    partOrder.incrementAndGet()
                );
                reasoningPart.setId(partId);
                reasoningPart.setMessage(currentMessage);
                reasoningPart.setType(ChatMessagePart.PartType.REASONING);
                reasoningPart.setContent(objectMapper.valueToTree(reasoningContent.toString()));

                chatMessagePartRepository.save(reasoningPart);
                logger.debug("Saved reasoning part: messageId={}, content={}", 
                    currentMessage.getId(), reasoningContent.toString());
                
                // Reset reasoning content for potential future reasoning chunks
                reasoningContent.setLength(0);
            } catch (Exception e) {
                logger.error("Failed to process reasoning finish: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onSourceUrl(StreamSourceUrlPart sourceUrl) {
            // Flush any accumulated text before creating source part to maintain proper ordering
            flushAccumulatedText();
            createMessagePart(ChatMessagePart.PartType.SOURCE_URL, sourceUrl);
        }

        @Override
        public void onSourceDocument(StreamSourceDocumentPart sourceDocument) {
            // Flush any accumulated text before creating source part to maintain proper ordering
            flushAccumulatedText();
            createMessagePart(ChatMessagePart.PartType.SOURCE_DOCUMENT, sourceDocument);
        }

        @Override
        public void onFile(StreamFilePart filePart) {
            // Flush any accumulated text before creating file part to maintain proper ordering
            flushAccumulatedText();
            createMessagePart(ChatMessagePart.PartType.FILE, filePart);
        }

        @Override
        public void onDataPart(StreamDataPart dataPart) {
            // Flush any accumulated text before creating data part to maintain proper ordering
            flushAccumulatedText();
            createMessagePart(ChatMessagePart.PartType.DATA, dataPart);
        }

        @Override
        public void onMessageMetadata(StreamMessageMetadataPart messageMetadata) {
            // Handle message metadata
            if (currentMessage != null) {
                try {
                    Object metadata = messageMetadata.getMessageMetadata();
                    com.fasterxml.jackson.databind.JsonNode metadataNode;
                    
                    if (metadata instanceof String metadataStr) {
                        // Parse JSON string into JsonNode
                        metadataNode = objectMapper.readTree(metadataStr);
                    } else {
                        // Convert object to JsonNode
                        metadataNode = objectMapper.valueToTree(metadata);
                    }
                    
                    currentMessage.setMetadata(metadataNode);
                    chatMessageRepository.save(currentMessage);
                    logger.debug("Updated message metadata for messageId={}", currentMessage.getId());
                } catch (Exception e) {
                    logger.error("Failed to process message metadata: {}", e.getMessage(), e);
                }
            }
        }

        @Override
        public void onUnknownStreamPart(Object streamPart) {
            logger.warn("Unknown stream part: {}", streamPart.getClass().getSimpleName());
        }
    }

    /**
     * Get or create a chat thread for the given ID and user.
     */
    private ChatThread getOrCreateChatThread(String threadId, User user) {
        UUID threadUuid = UUID.fromString(threadId);

        return chatThreadRepository
            .findById(threadUuid)
            .orElseGet(() -> {
                logger.debug("Creating new chat thread with ID: {}", threadUuid);
                ChatThread newThread = new ChatThread();
                newThread.setId(threadUuid);
                newThread.setUser(user);
                newThread.setTitle("New chat"); // Set title as expected by test
                return chatThreadRepository.save(newThread);
            });
    }

    /**
     * Persist user messages from the request and return the last message as parent for the assistant response.
     */
    private ChatMessage persistUserMessages(List<UIMessage> messages, ChatThread thread) {
        ChatMessage lastMessage = null;

        for (UIMessage uiMessage : messages) {
            // Check if message already exists
            UUID messageId = UUID.fromString(uiMessage.getId());
            var existingMessage = chatMessageRepository.findById(messageId);

            if (existingMessage.isPresent()) {
                lastMessage = existingMessage.get();
                continue;
            }

            // Create new user message
            ChatMessage userMessage = new ChatMessage();
            userMessage.setId(messageId);
            userMessage.setThread(thread);
            userMessage.setParentMessage(lastMessage); // Chain messages
            userMessage.setRole(ChatMessage.Role.valueOf(uiMessage.getRole().getValue().toUpperCase()));

            // Save message first
            userMessage = chatMessageRepository.save(userMessage);

            // Create message parts
            if (uiMessage.getParts() != null) {
                for (int i = 0; i < uiMessage.getParts().size(); i++) {
                    UIMessagePartsInner part = uiMessage.getParts().get(i);
                    createUserMessagePart(userMessage, part, i);
                }
            }

            // Update thread's selected leaf to this message
            thread.setSelectedLeafMessage(userMessage);
            thread.addMessage(userMessage);

            lastMessage = userMessage;
        }

        // Save thread with updated selected leaf
        chatThreadRepository.save(thread);

        return lastMessage;
    }

    /**
     * Create a message part for a user message.
     */
    private void createUserMessagePart(ChatMessage message, UIMessagePartsInner part, int orderIndex) {
        ChatMessagePart messagePart = new ChatMessagePart();
        ChatMessagePartId partId = new ChatMessagePartId(message.getId(), orderIndex);

        messagePart.setId(partId);
        messagePart.setMessage(message);
        messagePart.setType(ChatMessagePart.PartType.fromValue(part.getType()));

        // Set content based on part type
        if ("text".equals(part.getType()) && part.getText() != null) {
            // For text parts, store just the text content as a simple string
            messagePart.setContent(objectMapper.valueToTree(part.getText()));
        } else {
            // For other parts, store the entire part object
            messagePart.setContent(objectMapper.valueToTree(part));
        }

        // Save the part
        chatMessagePartRepository.save(messagePart);

        // Add to message's parts collection
        message.getParts().add(messagePart);
    }
}

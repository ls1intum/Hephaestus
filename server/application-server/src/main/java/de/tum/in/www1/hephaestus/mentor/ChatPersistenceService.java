package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.mentor.document.Document;
import de.tum.in.www1.hephaestus.mentor.document.DocumentKind;
import de.tum.in.www1.hephaestus.mentor.document.DocumentRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatMessagePartRepository chatMessagePartRepository;

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * Creates a processor for a complete chat request, handling thread and user message persistence.
     * This method encapsulates the full setup required for a chat request.
     */
    public StreamPartProcessor createProcessorForRequest(User user, ChatRequestDTO chatRequest) {
        // Get or create chat thread
        ChatThread thread = getOrCreateChatThread(chatRequest.id(), user);

        // Resolve parent by explicit previousMessageId when provided, else fallback to thread's selected leaf
        ChatMessage parent = null;
        if (chatRequest.previousMessageId() != null) {
            parent = chatMessageRepository.findById(chatRequest.previousMessageId()).orElse(null);
            // Ensure previous message is in the same thread
            if (parent != null && parent.getThread() != null && !parent.getThread().getId().equals(thread.getId())) {
                logger.warn(
                    "previousMessageId {} does not belong to thread {}, ignoring",
                    chatRequest.previousMessageId(),
                    thread.getId()
                );
                parent = null;
            }
        }
        // If no explicit parent provided, start a new branch from root (no parent)

        // Persist single user message and link to parent explicitly
        ChatMessage parentMessage = persistUserMessage(chatRequest.message(), thread, parent);

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
        private final AtomicInteger partOrder = new AtomicInteger(0);

        // Track text streams by ID for AI SDK v5 ID-based streaming
        private final ConcurrentHashMap<String, StringBuilder> textBuffers = new ConcurrentHashMap<>();

        // Track reasoning streams by ID for AI SDK v5 ID-based streaming
        private final ConcurrentHashMap<String, StringBuilder> reasoningBuffers = new ConcurrentHashMap<>();

        // Track tool calls by ID for AI SDK v5 parallel tool execution
        private final ConcurrentHashMap<String, ChatMessagePart> toolPartsById = new ConcurrentHashMap<>();

        // Track active streaming parts by ID for AI SDK v5 update-in-place behavior
        private final ConcurrentHashMap<String, ChatMessagePart> activeTextParts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ChatMessagePart> activeReasoningParts = new ConcurrentHashMap<>();

        // Track data parts by ID for replacement/merge updates
        private final ConcurrentHashMap<String, ChatMessagePart> dataPartsById = new ConcurrentHashMap<>();

        /**
         * Accumulator for building/updating a document from transient data-* parts in the stream.
         * "Transient" here means: not shown to the client UI as message parts, but MUST be used by the server
         * to construct and persist document content and versions.
         */
        private static class DocumentBuildState {

            UUID id; // Stable document ID across versions
            DocumentKind kind = DocumentKind.TEXT;
            String title;
            final StringBuilder content = new StringBuilder();
        }

        private DocumentBuildState currentDocument;

        public ChatStreamProcessor(User user, ChatThread thread, ChatMessage parentMessage) {
            this.user = user;
            this.thread = thread;
            this.parentMessage = parentMessage;
        }

        /**
         * Helper method for consistent part creation following AI SDK v5 principles.
         * Creates parts immediately when streaming starts, not when it ends.
         */
        private ChatMessagePart createMessagePart(ChatMessagePart.PartType type, String initialContent) {
            ChatMessagePart part = new ChatMessagePart();
            ChatMessagePartId partId = new ChatMessagePartId(
                currentMessage.getId(),
                partOrder.getAndIncrement() // Sequential ordering for ALL parts
            );
            part.setId(partId);
            part.setMessage(currentMessage);
            part.setType(type);

            // Create structured content based on part type
            if (type == ChatMessagePart.PartType.TEXT) {
                var structuredContent = objectMapper.createObjectNode();
                structuredContent.put("type", "text");
                structuredContent.put("text", initialContent);
                part.setContent(structuredContent);
            } else if (type == ChatMessagePart.PartType.REASONING) {
                var structuredContent = objectMapper.createObjectNode();
                structuredContent.put("type", "reasoning");
                structuredContent.put("text", initialContent);
                part.setContent(structuredContent);
            } else {
                // For non-text parts, store as simple string initially
                part.setContent(objectMapper.valueToTree(initialContent));
            }

            return part;
        }

        /**
         * Helper method that creates and saves a message part with originalType, adding it to the current message.
         * Use this when you want to create and immediately persist a part with a specific originalType.
         */
        private ChatMessagePart createAndSaveMessagePart(
            ChatMessagePart.PartType type,
            String initialContent,
            String originalType
        ) {
            ChatMessagePart part = createMessagePart(type, initialContent);

            // Set originalType if provided
            if (originalType != null) {
                part.setOriginalType(originalType);
            }

            // Save the part to database
            chatMessagePartRepository.save(part);

            // Add to message's parts collection for JPA relationship
            currentMessage.getParts().add(part);

            return part;
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

                // Handle start message metadata - the AI SDK expects this to be merged
                if (startPart.getMessageMetadata() != null) {
                    try {
                        Object metadata = startPart.getMessageMetadata();
                        JsonNode metadataNode;

                        if (metadata instanceof String metadataStr) {
                            metadataNode = objectMapper.readTree(metadataStr);
                        } else {
                            metadataNode = objectMapper.valueToTree(metadata);
                        }

                        currentMessage.setMetadata(metadataNode);
                        logger.debug("Set start metadata for messageId={}", currentMessage.getId());
                    } catch (Exception e) {
                        logger.error("Failed to process start metadata: {}", e.getMessage(), e);
                    }
                }

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
        public void onTextStart(StreamTextStartPart textStartPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received text start without message start");
                    return;
                }

                // Create part immediately when streaming starts
                String textId = textStartPart.getId();
                ChatMessagePart textPart = createAndSaveMessagePart(ChatMessagePart.PartType.TEXT, "", "text");

                // Track for updates during streaming
                activeTextParts.put(textId, textPart);
                textBuffers.put(textId, new StringBuilder());

                // If provider metadata present, initialize it on content so future deltas keep it
                if (textStartPart.getProviderMetadata() != null) {
                    var structuredContent = objectMapper.createObjectNode();
                    structuredContent.put("type", "text");
                    structuredContent.put("text", "");
                    structuredContent.set(
                        "providerMetadata",
                        objectMapper.valueToTree(textStartPart.getProviderMetadata())
                    );
                    textPart.setContent(structuredContent);
                    chatMessagePartRepository.save(textPart);
                }

                logger.debug(
                    "Created text part immediately for ID: {} in message: {} with order: {}",
                    textId,
                    currentMessage.getId(),
                    textPart.getId().getOrderIndex()
                );
            } catch (Exception e) {
                logger.error("Failed to process text start: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onTextDelta(StreamTextDeltaPart textDeltaPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received text delta without message start");
                    return;
                }

                // Update existing part content
                String textId = textDeltaPart.getId();
                StringBuilder buffer = textBuffers.get(textId);
                ChatMessagePart textPart = activeTextParts.get(textId);

                if (buffer == null || textPart == null) {
                    logger.warn("Received text delta for unknown text ID: {}", textId);
                    return;
                }

                // Accumulate delta
                buffer.append(textDeltaPart.getDelta());

                // Create structured content with type and text fields for proper conversion
                var structuredContent = objectMapper.createObjectNode();
                structuredContent.put("type", "text");
                structuredContent.put("text", buffer.toString());

                // If provider metadata present on this delta, merge/override
                if (textDeltaPart.getProviderMetadata() != null) {
                    structuredContent.set(
                        "providerMetadata",
                        objectMapper.valueToTree(textDeltaPart.getProviderMetadata())
                    );
                } else if (textPart.getContent() != null && textPart.getContent().has("providerMetadata")) {
                    // preserve previously set providerMetadata
                    structuredContent.set("providerMetadata", textPart.getContent().get("providerMetadata"));
                }

                // Update the database part with structured content
                textPart.setContent(structuredContent);
                chatMessagePartRepository.save(textPart);
            } catch (Exception e) {
                logger.error("Failed to process text delta: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onTextEnd(StreamTextEndPart textEndPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received text end without message start");
                    return;
                }

                // Just finalize existing part, don't create new one
                String textId = textEndPart.getId();
                ChatMessagePart textPart = activeTextParts.get(textId);

                if (textPart != null) {
                    // Part already exists with final content - just mark as done
                    // (Note: In our implementation, we don't have a state field, so we just log)
                    logger.debug("Finalized text part for ID: {} in message: {}", textId, currentMessage.getId());

                    // Cleanup tracking
                    activeTextParts.remove(textId);
                }

                textBuffers.remove(textId);
            } catch (Exception e) {
                logger.error("Failed to process text end: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolInputStart(StreamToolInputStartPart toolInputStart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received tool input start without message start");
                    return;
                }

                // Create tool part immediately when tool input starts
                String toolCallId = toolInputStart.getToolCallId();
                String toolName = toolInputStart.getToolName();

                // Use the correct tool type for input-streaming state
                ChatMessagePart toolPart = createAndSaveMessagePart(
                    ChatMessagePart.PartType.TOOL,
                    "",
                    "tool-" + toolName
                );

                // Create initial tool part content in AI SDK v5 format
                var toolContent = objectMapper.createObjectNode();
                toolContent.put("toolCallId", toolCallId);
                toolContent.put("type", "tool-" + toolName);
                toolContent.put("state", "input-streaming");
                toolContent.put("input", (String) null); // Will be updated in onToolInputAvailable
                toolContent.put("output", (String) null);
                toolContent.put("errorText", (String) null);
                // Optional flags from stream start
                if (toolInputStart.getProviderExecuted() != null) {
                    toolContent.put("providerExecuted", toolInputStart.getProviderExecuted());
                }
                if (toolInputStart.getDynamic() != null) {
                    toolContent.put("dynamic", toolInputStart.getDynamic());
                }
                toolPart.setContent(toolContent);

                // Update content in database since createMessagePart already saved it
                chatMessagePartRepository.save(toolPart);

                // Track for updates
                toolPartsById.put(toolCallId, toolPart);

                logger.debug(
                    "Created tool part immediately for toolCallId: {} with toolName: {} in message: {}",
                    toolCallId,
                    toolName,
                    currentMessage.getId()
                );
            } catch (Exception e) {
                logger.error("Failed to process tool input start: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolInputDelta(StreamToolInputDeltaPart toolInputDelta) {
            // Delta parts are accumulated and handled in onToolInputAvailable
            logger.debug(
                "Tool input delta received for message: {}",
                currentMessage != null ? currentMessage.getId() : "null"
            );
        }

        @Override
        public void onToolInputAvailable(StreamToolInputAvailablePart toolInput) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received tool input without message start");
                    return;
                }

                // Update existing tool part OR create if missing (edge case fallback)
                String toolCallId = toolInput.getToolCallId();
                ChatMessagePart toolPart = toolPartsById.get(toolCallId);

                if (toolPart == null) {
                    // ⚠️ Edge case: Input available without prior start - create tool part immediately
                    logger.warn(
                        "Received tool input available without prior start for toolCallId: {} - creating tool part",
                        toolCallId
                    );
                    toolPart = createAndSaveMessagePart(
                        ChatMessagePart.PartType.TOOL,
                        "",
                        "tool-" + toolInput.getToolName()
                    );

                    // Create initial tool part content
                    var toolContent = objectMapper.createObjectNode();
                    toolContent.put("toolCallId", toolCallId);
                    toolContent.put("type", "tool-" + toolInput.getToolName());
                    toolContent.put("state", "input-available");
                    toolContent.put("input", (String) null);
                    toolContent.put("output", (String) null);
                    toolContent.put("errorText", (String) null);
                    // Optional flags
                    if (toolInput.getProviderExecuted() != null) {
                        toolContent.put("providerExecuted", toolInput.getProviderExecuted());
                    }
                    if (toolInput.getDynamic() != null) {
                        toolContent.put("dynamic", toolInput.getDynamic());
                    }
                    toolPart.setContent(toolContent);

                    // Update content in database since createAndSaveMessagePart already saved it
                    chatMessagePartRepository.save(toolPart);
                    toolPartsById.put(toolCallId, toolPart);
                }

                // Update the tool part content and type
                var toolContent = (ObjectNode) toolPart.getContent();
                toolContent.put("state", "input-available");

                // Tool part type remains TOOL - state is stored in content

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
                    toolContent.set("input", objectMapper.valueToTree(input));
                }

                // store providerExecuted if provided
                if (toolInput.getProviderExecuted() != null) {
                    toolContent.put("providerExecuted", toolInput.getProviderExecuted());
                }
                if (toolInput.getDynamic() != null) {
                    toolContent.put("dynamic", toolInput.getDynamic());
                }

                // store provider metadata if provided (map stream providerMetadata -> UI callProviderMetadata)
                try {
                    Object streamProviderMetadata = toolInput.getProviderMetadata();
                    if (streamProviderMetadata != null) {
                        toolContent.set("callProviderMetadata", objectMapper.valueToTree(streamProviderMetadata));
                    }
                } catch (Exception ignored) {}

                toolPart.setContent(toolContent);
                chatMessagePartRepository.save(toolPart);

                logger.debug(
                    "Updated tool part input for toolCallId: {} in message: {}",
                    toolCallId,
                    currentMessage.getId()
                );
            } catch (Exception e) {
                logger.error("Failed to process tool input available: {}", e.getMessage(), e);
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

                // Update the tool part with output by tool call ID
                String toolCallId = toolOutput.getToolCallId();
                ChatMessagePart toolPart = toolPartsById.get(toolCallId);

                if (toolPart != null) {
                    var existingContent = (ObjectNode) toolPart.getContent();

                    // Update state to output-available - type remains TOOL
                    existingContent.put("state", "output-available");

                    // Handle output - if it's a string, parse it; otherwise use as-is
                    Object output = toolOutput.getOutput();
                    if (output instanceof String outputStr) {
                        try {
                            JsonNode outputNode = objectMapper.readTree(outputStr);
                            existingContent.set("output", outputNode);
                        } catch (Exception e) {
                            logger.warn("Failed to parse tool output JSON, storing as string: {}", e.getMessage());
                            existingContent.put("output", outputStr);
                        }
                    } else {
                        // Output is already an object, convert to JsonNode
                        existingContent.set("output", objectMapper.valueToTree(output));
                    }

                    // store providerExecuted if provided
                    if (toolOutput.getProviderExecuted() != null) {
                        existingContent.put("providerExecuted", toolOutput.getProviderExecuted());
                    }
                    if (toolOutput.getDynamic() != null) {
                        existingContent.put("dynamic", toolOutput.getDynamic());
                    }

                    chatMessagePartRepository.save(toolPart);
                    logger.debug(
                        "Updated tool part with output: messageId={}, toolCallId={}, state=output-available",
                        currentMessage.getId(),
                        toolCallId
                    );

                    // Remove the reference since this tool call is complete
                    toolPartsById.remove(toolCallId);
                } else {
                    logger.warn("Received tool output for unknown tool call: {}", toolCallId);
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

                // Update the tool part with error by tool call ID
                ChatMessagePart toolPart = toolPartsById.get(toolCallId);

                if (toolPart == null) {
                    // ⚠️ Edge case: Tool error without prior start - create tool part for error
                    logger.warn(
                        "Received tool output error without prior tool part for toolCallId: {} - creating error tool part",
                        toolCallId
                    );
                    toolPart = createAndSaveMessagePart(ChatMessagePart.PartType.TOOL, "", "tool-error");

                    // Create tool part content with error state
                    var toolContent = objectMapper.createObjectNode();
                    toolContent.put("toolCallId", toolCallId);
                    toolContent.put("type", "tool-error");
                    toolContent.put("state", "output-error");
                    toolContent.put("input", (String) null);
                    toolContent.put("output", (String) null);
                    toolContent.put("errorText", errorText);
                    if (errorPart.getProviderExecuted() != null) {
                        toolContent.put("providerExecuted", errorPart.getProviderExecuted());
                    }
                    if (errorPart.getDynamic() != null) {
                        toolContent.put("dynamic", errorPart.getDynamic());
                    }
                    toolPart.setContent(toolContent);

                    // Update content in database since createAndSaveMessagePart already saved it
                    chatMessagePartRepository.save(toolPart);
                    toolPartsById.put(toolCallId, toolPart);
                } else {
                    // Update existing tool part with error
                    var existingContent = (ObjectNode) toolPart.getContent();
                    existingContent.put("errorText", errorText);
                    existingContent.put("state", "output-error");
                    // Set output to undefined (null) when there's an error, as per AI SDK
                    existingContent.put("output", (String) null);

                    chatMessagePartRepository.save(toolPart);
                }

                logger.debug(
                    "Updated tool part with error: messageId={}, toolCallId={}, error={}, state=output-error",
                    currentMessage.getId(),
                    toolCallId,
                    errorText
                );

                // Remove the reference since this tool call is complete (with error)
                toolPartsById.remove(toolCallId);
            } catch (Exception e) {
                logger.error("Failed to process tool output error: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolInputError(StreamToolInputErrorPart inputError) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received tool input error without message start");
                    return;
                }

                String toolCallId = inputError.getToolCallId();
                ChatMessagePart toolPart = toolPartsById.get(toolCallId);

                if (toolPart == null) {
                    // create a tool part to reflect the error
                    toolPart = createAndSaveMessagePart(
                        ChatMessagePart.PartType.TOOL,
                        "",
                        "tool-" + inputError.getToolName()
                    );
                    var content = objectMapper.createObjectNode();
                    content.put("toolCallId", toolCallId);
                    content.put("type", "tool-" + inputError.getToolName());
                    toolPart.setContent(content);
                }

                var existingContent = (ObjectNode) toolPart.getContent();
                existingContent.put("state", "output-error");
                existingContent.put("errorText", inputError.getErrorText());

                // include raw input for diagnostics; map to rawInput per AI SDK client usage
                Object input = inputError.getInput();
                if (input != null) {
                    existingContent.set("rawInput", objectMapper.valueToTree(input));
                }

                // store callProviderMetadata if provided (from stream providerMetadata)
                try {
                    Object streamProviderMetadata = inputError.getProviderMetadata();
                    if (streamProviderMetadata != null) {
                        existingContent.set("callProviderMetadata", objectMapper.valueToTree(streamProviderMetadata));
                    }
                } catch (Exception ignored) {}
                if (inputError.getProviderExecuted() != null) {
                    existingContent.put("providerExecuted", inputError.getProviderExecuted());
                }
                if (inputError.getDynamic() != null) {
                    existingContent.put("dynamic", inputError.getDynamic());
                }

                chatMessagePartRepository.save(toolPart);

                // complete this tool call
                toolPartsById.remove(toolCallId);
            } catch (Exception e) {
                logger.error("Failed to process tool input error: {}", e.getMessage(), e);
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

                // Handle finish metadata - the AI SDK expects this to be merged last
                if (finishPart.getMessageMetadata() != null) {
                    try {
                        Object metadata = finishPart.getMessageMetadata();
                        JsonNode newMetadataNode;

                        if (metadata instanceof String metadataStr) {
                            newMetadataNode = objectMapper.readTree(metadataStr);
                        } else {
                            newMetadataNode = objectMapper.valueToTree(metadata);
                        }

                        // Merge with existing metadata
                        JsonNode mergedMetadata = mergeMetadata(currentMessage.getMetadata(), newMetadataNode);
                        currentMessage.setMetadata(mergedMetadata);
                        logger.debug("Merged finish metadata for messageId={}", currentMessage.getId());
                    } catch (Exception e) {
                        logger.error("Failed to process finish metadata: {}", e.getMessage(), e);
                    }
                }

                logger.debug("Message stream completed: messageId={}", currentMessage.getId());

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

        /**
         * Merges two JsonNode objects deeply, following AI SDK metadata merge semantics.
         * The newMetadata takes precedence over existing metadata for conflicting keys.
         */
        private JsonNode mergeMetadata(JsonNode existingMetadata, JsonNode newMetadata) {
            if (existingMetadata == null) {
                return newMetadata;
            }
            if (newMetadata == null) {
                return existingMetadata;
            }

            if (!existingMetadata.isObject() || !newMetadata.isObject()) {
                // If either is not an object, new metadata replaces existing
                return newMetadata;
            }

            // Deep merge objects
            ObjectNode merged = (ObjectNode) existingMetadata.deepCopy();
            ObjectNode newObjectNode = (ObjectNode) newMetadata;

            newObjectNode
                .fieldNames()
                .forEachRemaining(fieldName -> {
                    JsonNode newValue = newObjectNode.get(fieldName);

                    if (merged.has(fieldName) && merged.get(fieldName).isObject() && newValue.isObject()) {
                        // Recursively merge nested objects
                        merged.set(fieldName, mergeMetadata(merged.get(fieldName), newValue));
                    } else {
                        // Replace or add new value
                        merged.set(fieldName, newValue);
                    }
                });

            return merged;
        }

        @Override
        public void onStreamError(StreamErrorPart errorPart) {
            lock.lock();
            try {
                logger.error("Stream error occurred: {}", errorPart);

                if (currentMessage != null) {
                    // Create error part directly using specialized approach
                    ChatMessagePart errorMessagePart = new ChatMessagePart();
                    ChatMessagePartId partId = new ChatMessagePartId(
                        currentMessage.getId(),
                        partOrder.incrementAndGet()
                    );
                    errorMessagePart.setId(partId);
                    errorMessagePart.setMessage(currentMessage);
                    errorMessagePart.setType(ChatMessagePart.PartType.TEXT);
                    errorMessagePart.setContent(objectMapper.valueToTree("Error: " + errorPart.toString()));

                    chatMessagePartRepository.save(errorMessagePart);
                    logger.debug("Saved error part: messageId={}", currentMessage.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to process stream error: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        // Implement remaining StreamPartProcessor methods...
        @Override
        public void onStepStart(StreamStepStartPart stepStart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received step start without message start");
                    return;
                }

                // Step start parts should be persisted as actual parts according to AI SDK
                ChatMessagePart stepPart = new ChatMessagePart();
                ChatMessagePartId partId = new ChatMessagePartId(currentMessage.getId(), partOrder.getAndIncrement());
                stepPart.setId(partId);
                stepPart.setMessage(currentMessage);
                stepPart.setType(ChatMessagePart.PartType.STEP_START);
                stepPart.setContent(objectMapper.createObjectNode()); // Empty content for step-start

                chatMessagePartRepository.save(stepPart);
                logger.debug("Saved step-start part: messageId={}", currentMessage.getId());
            } catch (Exception e) {
                logger.error("Failed to process step start: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onStepFinish(StreamStepFinishPart stepFinish) {
            lock.lock();
            try {
                // Step finish parts are flow control markers, not persisted as parts in AI SDK
                logger.debug("Step finished for message: {}", currentMessage != null ? currentMessage.getId() : "null");
            } catch (Exception e) {
                logger.error("Failed to process step finish: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onReasoningStart(StreamReasoningStartPart reasoningStartPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received reasoning start without message start");
                    return;
                }

                // Create reasoning part immediately when streaming starts
                String reasoningId = reasoningStartPart.getId();
                ChatMessagePart reasoningPart = createAndSaveMessagePart(
                    ChatMessagePart.PartType.REASONING,
                    "",
                    "reasoning"
                );

                // Track for updates during streaming
                activeReasoningParts.put(reasoningId, reasoningPart);
                reasoningBuffers.put(reasoningId, new StringBuilder());

                // Initialize with provider metadata if present
                Object startProviderMetadata = reasoningStartPart.getProviderMetadata();
                if (startProviderMetadata != null) {
                    var structured = objectMapper.createObjectNode();
                    structured.put("type", "reasoning");
                    structured.put("text", "");
                    structured.set("providerMetadata", objectMapper.valueToTree(startProviderMetadata));
                    reasoningPart.setContent(structured);
                    chatMessagePartRepository.save(reasoningPart);
                }

                logger.debug(
                    "Created reasoning part immediately for ID: {} in message: {}",
                    reasoningId,
                    currentMessage.getId()
                );
            } catch (Exception e) {
                logger.error("Failed to process reasoning start: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onReasoningDelta(StreamReasoningDeltaPart reasoningDeltaPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received reasoning delta without message start");
                    return;
                }

                // Update existing reasoning part content
                String reasoningId = reasoningDeltaPart.getId();
                StringBuilder buffer = reasoningBuffers.get(reasoningId);
                ChatMessagePart reasoningPart = activeReasoningParts.get(reasoningId);

                if (buffer == null || reasoningPart == null) {
                    logger.warn("Received reasoning delta for unknown reasoning ID: {}", reasoningId);
                    return;
                }

                // Accumulate delta
                buffer.append(reasoningDeltaPart.getDelta());

                // Update the database part with current accumulated content in structured form
                com.fasterxml.jackson.databind.node.ObjectNode structured;
                if (reasoningPart.getContent() != null && reasoningPart.getContent().isObject()) {
                    structured = (com.fasterxml.jackson.databind.node.ObjectNode) reasoningPart.getContent();
                } else {
                    structured = objectMapper.createObjectNode();
                    structured.put("type", "reasoning");
                }
                structured.put("text", buffer.toString());
                Object deltaProviderMetadata = reasoningDeltaPart.getProviderMetadata();
                if (deltaProviderMetadata != null) {
                    structured.set("providerMetadata", objectMapper.valueToTree(deltaProviderMetadata));
                }
                reasoningPart.setContent(structured);
                chatMessagePartRepository.save(reasoningPart);
            } catch (Exception e) {
                logger.error("Failed to process reasoning delta: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onReasoningEnd(StreamReasoningEndPart reasoningEndPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received reasoning end without message start");
                    return;
                }

                // Just finalize existing reasoning part
                String reasoningId = reasoningEndPart.getId();
                ChatMessagePart reasoningPart = activeReasoningParts.get(reasoningId);

                if (reasoningPart != null) {
                    logger.debug(
                        "Finalized reasoning part for ID: {} in message: {}",
                        reasoningId,
                        currentMessage.getId()
                    );

                    // Cleanup tracking
                    activeReasoningParts.remove(reasoningId);
                }

                reasoningBuffers.remove(reasoningId);
            } catch (Exception e) {
                logger.error("Failed to process reasoning end: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onSourceUrl(StreamSourceUrlPart sourceUrl) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received source URL part without message start");
                    return;
                }

                // Create source URL part immediately
                ChatMessagePart sourcePart = createAndSaveMessagePart(
                    ChatMessagePart.PartType.SOURCE_URL,
                    sourceUrl.getUrl(),
                    "source-url"
                );

                // Add additional source metadata
                var sourceContent = objectMapper.createObjectNode();
                // keep sourceId for traceability and parity with UI chunk schema
                if (sourceUrl.getSourceId() != null) {
                    sourceContent.put("sourceId", sourceUrl.getSourceId());
                }
                sourceContent.put("url", sourceUrl.getUrl());
                sourceContent.put("title", sourceUrl.getTitle());
                if (sourceUrl.getProviderMetadata() != null) {
                    sourceContent.set("providerMetadata", objectMapper.valueToTree(sourceUrl.getProviderMetadata()));
                }
                sourcePart.setContent(sourceContent);

                // Update content in database
                chatMessagePartRepository.save(sourcePart);
                logger.debug(
                    "Created source URL part: messageId={}, url={}",
                    currentMessage.getId(),
                    sourceUrl.getUrl()
                );
            } catch (Exception e) {
                logger.error("Failed to process source URL part: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onSourceDocument(StreamSourceDocumentPart sourceDocument) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received source document part without message start");
                    return;
                }

                // Create source document part immediately
                ChatMessagePart sourcePart = createAndSaveMessagePart(
                    ChatMessagePart.PartType.SOURCE_DOCUMENT,
                    sourceDocument.getTitle(),
                    "source-document"
                );

                var sourceContent = objectMapper.createObjectNode();
                sourceContent.put("sourceId", sourceDocument.getSourceId());
                sourceContent.put("title", sourceDocument.getTitle());
                sourceContent.put("mediaType", sourceDocument.getMediaType());
                if (sourceDocument.getFilename() != null) {
                    sourceContent.put("filename", sourceDocument.getFilename());
                }
                if (sourceDocument.getProviderMetadata() != null) {
                    sourceContent.set(
                        "providerMetadata",
                        objectMapper.valueToTree(sourceDocument.getProviderMetadata())
                    );
                }
                sourcePart.setContent(sourceContent);

                chatMessagePartRepository.save(sourcePart);
                logger.debug(
                    "Created source document part: messageId={}, sourceId={}",
                    currentMessage.getId(),
                    sourceDocument.getSourceId()
                );
            } catch (Exception e) {
                logger.error("Failed to process source document part: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onFile(StreamFilePart filePart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received file part without message start");
                    return;
                }

                // Create file part immediately
                ChatMessagePart fileMessagePart = createAndSaveMessagePart(
                    ChatMessagePart.PartType.FILE,
                    filePart.getUrl(),
                    "file"
                );

                var fileContent = objectMapper.createObjectNode();
                fileContent.put("url", filePart.getUrl());
                fileContent.put("mediaType", filePart.getMediaType());
                if (filePart.getProviderMetadata() != null) {
                    // Store under providerMetadata to align with UI file part schema
                    fileContent.set("providerMetadata", objectMapper.valueToTree(filePart.getProviderMetadata()));
                }
                fileMessagePart.setContent(fileContent);

                // Update content in database
                chatMessagePartRepository.save(fileMessagePart);
                logger.debug("Created file part: messageId={}, url={}", currentMessage.getId(), filePart.getUrl());
            } catch (Exception e) {
                logger.error("Failed to process file part: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onDataPart(StreamDataPart dataPart) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received data part without message start");
                    return;
                }

                // Special handling for document construction via transient data-* parts
                String partType = dataPart.getType();
                if (partType != null && partType.startsWith("data-")) {
                    handleDocumentDataPart(dataPart);
                    // Never persist these as chat message parts – they are control/data signals for document ops
                    return;
                }

                // For any other data parts: keep existing semantics
                Boolean isTransient = dataPart.getTransient();
                if (Boolean.TRUE.equals(isTransient)) {
                    logger.debug("Skipping transient data part: type={}", dataPart.getType());
                    return;
                }

                // Check if this data part has an ID for replacement/merge updates
                String dataId = dataPart.getId();
                if (dataId != null && dataPartsById.containsKey(dataId)) {
                    // Update existing data part
                    ChatMessagePart existingPart = dataPartsById.get(dataId);
                    updateDataPartContent(existingPart, dataPart);
                    chatMessagePartRepository.save(existingPart);
                    logger.debug(
                        "Updated existing data part: messageId={}, dataId={}, type={}",
                        currentMessage.getId(),
                        dataId,
                        dataPart.getType()
                    );
                } else {
                    // Create new data part
                    ChatMessagePart newDataPart = createDataPart(dataPart);
                    chatMessagePartRepository.save(newDataPart);

                    // Track by ID if provided
                    if (dataId != null) {
                        dataPartsById.put(dataId, newDataPart);
                    }

                    logger.debug(
                        "Created new data part: messageId={}, dataId={}, type={}",
                        currentMessage.getId(),
                        dataId,
                        dataPart.getType()
                    );
                }
            } catch (Exception e) {
                logger.error("Failed to process data part: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        /**
         * Creates a new data part from a StreamDataPart
         */
        private ChatMessagePart createDataPart(StreamDataPart dataPart) {
            // Use consistent helper method
            ChatMessagePart part = createMessagePart(ChatMessagePart.PartType.DATA, "");
            part.setOriginalType(dataPart.getType()); // Set originalType for data parts

            updateDataPartContent(part, dataPart);
            return part;
        }

        /**
         * Updates the content of a data part, handling both replacement and merge scenarios
         */
        private void updateDataPartContent(ChatMessagePart part, StreamDataPart dataPart) {
            var dataContent = objectMapper.createObjectNode();
            dataContent.put("type", dataPart.getType());

            // Keep originalType in sync with the latest data-* type for UI fidelity
            part.setOriginalType(dataPart.getType());

            // Add ID if present
            if (dataPart.getId() != null) {
                dataContent.put("id", dataPart.getId());
            }

            // Handle data - if it's a string, try to parse it as JSON first, otherwise store as string
            Object data = dataPart.getData();
            if (data instanceof String dataStr) {
                if (dataStr.trim().startsWith("{") || dataStr.trim().startsWith("[")) {
                    try {
                        JsonNode dataNode = objectMapper.readTree(dataStr);
                        dataContent.set("data", dataNode);
                    } catch (Exception e) {
                        logger.warn("Failed to parse data JSON, storing as string: {}", e.getMessage());
                        dataContent.put("data", dataStr);
                    }
                } else {
                    // Not JSON, store as string directly
                    dataContent.put("data", dataStr);
                }
            } else {
                // Data is already an object, convert to JsonNode
                dataContent.set("data", objectMapper.valueToTree(data));
            }

            part.setContent(dataContent);
        }

        /**
         * Handle server-side document building/updating from AI SDK v5 data-* transient parts.
         * Supported control parts (type -> data expected):
         *  - data-kind:       "text" | ... -> sets DocumentKind
         *  - data-title:      string        -> sets title
         *  - data-id:         UUID string   -> sets/locks the document id
         *  - data-textDelta:  string        -> append to content buffer
         *  - data-clear:      any           -> clears content buffer
         *  - data-finish:     any           -> persists a new document version
         */
        private void handleDocumentDataPart(StreamDataPart dataPart) {
            try {
                String type = dataPart.getType();
                Object data = dataPart.getData();

                // Ensure we have a build state to work with
                if (currentDocument == null) {
                    currentDocument = new DocumentBuildState();
                }

                switch (type) {
                    case "data-kind" -> {
                        if (data instanceof String s && !s.isBlank()) {
                            try {
                                currentDocument.kind = DocumentKind.fromValue(s);
                            } catch (IllegalArgumentException ex) {
                                logger.warn("Unknown document kind '{}', defaulting to TEXT", s);
                                currentDocument.kind = DocumentKind.TEXT;
                            }
                        }
                    }
                    case "data-title" -> {
                        if (data instanceof String s) {
                            currentDocument.title = s;
                        }
                    }
                    case "data-id" -> {
                        if (data instanceof String s && !s.isBlank()) {
                            try {
                                currentDocument.id = UUID.fromString(s.trim());
                            } catch (IllegalArgumentException ex) {
                                logger.warn("Invalid document UUID received in data-id: {}", s);
                            }
                        }
                    }
                    case "data-textDelta" -> {
                        if (data instanceof String s) {
                            currentDocument.content.append(s);
                        }
                    }
                    case "data-clear" -> {
                        currentDocument.content.setLength(0);
                    }
                    case "data-finish" -> {
                        // On finish: persist a document version (create or update)
                        if (currentDocument.id == null) {
                            // If no explicit id provided so far, generate a new one for creation
                            currentDocument.id = UUID.randomUUID();
                        }

                        // Try to reuse existing doc attributes when this is an update (id provided without title/kind)
                        String title = currentDocument.title;
                        DocumentKind kind = currentDocument.kind;

                        var latestExisting = documentRepository.findFirstByIdAndUserOrderByVersionNumberDesc(
                            currentDocument.id,
                            thread.getUser()
                        );
                        if (latestExisting.isPresent()) {
                            if (title == null || title.isBlank()) {
                                title = latestExisting.get().getTitle();
                            }
                            if (kind == null) {
                                kind = latestExisting.get().getKind();
                            }
                        }

                        if (title == null || title.isBlank()) {
                            title = "Untitled";
                        }
                        if (kind == null) {
                            kind = DocumentKind.TEXT;
                        }

                        String content = currentDocument.content.toString();

                        int nextVersion = latestExisting.map(d -> d.getVersionNumber() + 1).orElse(1);
                        Document toSave = new Document(
                            currentDocument.id,
                            nextVersion,
                            title,
                            content,
                            kind,
                            thread.getUser()
                        );
                        documentRepository.save(toSave);

                        logger.info(
                            "Persisted document version: id={}, title='{}', kind={}, contentLen={}",
                            toSave.getId(),
                            title,
                            toSave.getKind(),
                            content != null ? content.length() : 0
                        );
                        // Keep build state for potential further updates in the same stream
                    }
                    default -> {
                        // For other data-* types not related to documents, ignore silently
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to handle document data part {}: {}", dataPart.getType(), e.getMessage(), e);
            }
        }

        @Override
        public void onMessageMetadata(StreamMessageMetadataPart messageMetadata) {
            lock.lock();
            try {
                if (currentMessage == null) {
                    logger.warn("Received message metadata without message start");
                    return;
                }

                // Handle message metadata - this should be merged with existing metadata
                Object metadata = messageMetadata.getMessageMetadata();
                JsonNode newMetadataNode;

                if (metadata instanceof String metadataStr) {
                    // Parse JSON string into JsonNode
                    newMetadataNode = objectMapper.readTree(metadataStr);
                } else {
                    // Convert object to JsonNode
                    newMetadataNode = objectMapper.valueToTree(metadata);
                }

                // Merge with existing metadata
                JsonNode mergedMetadata = mergeMetadata(currentMessage.getMetadata(), newMetadataNode);
                currentMessage.setMetadata(mergedMetadata);
                chatMessageRepository.save(currentMessage);
                logger.debug("Merged message metadata for messageId={}", currentMessage.getId());
            } catch (Exception e) {
                logger.error("Failed to process message metadata: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
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
        UUID parsedUuid;
        try {
            parsedUuid = UUID.fromString(threadId);
        } catch (IllegalArgumentException ex) {
            parsedUuid = UUID.randomUUID();
            logger.warn("Invalid threadId '{}', generating new UUID {}", threadId, parsedUuid);
        }
        final UUID threadUuid = parsedUuid;

        return chatThreadRepository
            .findById(threadUuid)
            .orElseGet(() -> {
                logger.debug("Creating new chat thread with ID: {}", threadUuid);
                ChatThread newThread = new ChatThread();
                newThread.setId(threadUuid);
                newThread.setUser(user);

                // Format current date/time as thread title
                LocalDateTime now = LocalDateTime.now();
                String formattedTitle = now.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"));
                newThread.setTitle(formattedTitle);

                return chatThreadRepository.save(newThread);
            });
    }

    /**
     * Persist user messages from the request and return the last message as parent for the assistant response.
     */
    private ChatMessage persistUserMessage(UIMessage uiMessage, ChatThread thread, ChatMessage parent) {
        if (uiMessage == null) return parent;

        // Check if message already exists
        UUID messageId = UUID.fromString(uiMessage.getId());
        var existingMessage = chatMessageRepository.findById(messageId);
        if (existingMessage.isPresent()) {
            return existingMessage.get();
        }

        // Create new user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(messageId);
        userMessage.setThread(thread);
        userMessage.setParentMessage(parent); // Use explicit parent or selected leaf
        try {
            var role = uiMessage.getRole() != null ? uiMessage.getRole().getValue() : "user";
            userMessage.setRole(ChatMessage.Role.valueOf(role.toUpperCase()));
        } catch (Exception ex) {
            logger.warn("Invalid role on UIMessage {}, defaulting to USER: {}", messageId, ex.getMessage());
            userMessage.setRole(ChatMessage.Role.USER);
        }

        // Save message first
        userMessage = chatMessageRepository.save(userMessage);

        // Create message parts
        if (uiMessage.getParts() != null) {
            for (int i = 0; i < uiMessage.getParts().size(); i++) {
                UIMessagePartsInner part = uiMessage.getParts().get(i);
                if (part != null) {
                    createUserMessagePart(userMessage, part, i);
                }
            }
        }

        // Update thread's selected leaf to this message
        thread.setSelectedLeafMessage(userMessage);
        thread.addMessage(userMessage);
        chatThreadRepository.save(thread);

        return userMessage;
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
        messagePart.setOriginalType(part.getType());

        // Create clean content with only non-null fields to avoid storing null values
        // Use reflection to dynamically get all properties and filter out nulls
        messagePart.setContent(createCleanJsonNode(part));

        // Save the part
        chatMessagePartRepository.save(messagePart);

        // Add to message's parts collection
        message.getParts().add(messagePart);
    }

    /**
     * Creates a clean JsonNode from an object, including only non-null properties.
     * Uses reflection to dynamically get all getter methods and their @JsonProperty annotations.
     * Filters out default/irrelevant values for cleaner storage.
     */
    private JsonNode createCleanJsonNode(Object object) {
        ObjectNode content = objectMapper.createObjectNode();
        String messageType = null;

        try {
            Class<?> clazz = object.getClass();
            Method[] methods = clazz.getMethods();

            // First pass: get the type to help with relevance filtering
            for (Method method : methods) {
                if (
                    method.getName().equals("getType") &&
                    method.getParameterCount() == 0 &&
                    method.isAnnotationPresent(JsonProperty.class)
                ) {
                    try {
                        Object typeValue = method.invoke(object);
                        if (typeValue instanceof String) {
                            messageType = (String) typeValue;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get type for relevance filtering: {}", e.getMessage());
                    }
                    break;
                }
            }

            // Second pass: process all properties with type-aware filtering
            for (Method method : methods) {
                // Look for getter methods with @JsonProperty annotation
                if (
                    method.getName().startsWith("get") &&
                    method.getParameterCount() == 0 &&
                    method.isAnnotationPresent(JsonProperty.class)
                ) {
                    JsonProperty jsonProperty = method.getAnnotation(JsonProperty.class);
                    String propertyName = jsonProperty.value();

                    try {
                        Object value = method.invoke(object);
                        if (value != null && isRelevantValue(propertyName, value, messageType)) {
                            if (value instanceof String) {
                                content.put(propertyName, (String) value);
                            } else if (value instanceof Boolean) {
                                content.put(propertyName, (Boolean) value);
                            } else if (value instanceof Integer) {
                                content.put(propertyName, (Integer) value);
                            } else if (value instanceof Long) {
                                content.put(propertyName, (Long) value);
                            } else if (value instanceof Double) {
                                content.put(propertyName, (Double) value);
                            } else {
                                // For complex objects, convert to JsonNode
                                content.set(propertyName, objectMapper.valueToTree(value));
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get property value for {}: {}", propertyName, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create clean JsonNode: {}", e.getMessage(), e);
            // Fallback to direct conversion
            return objectMapper.valueToTree(object);
        }

        return content;
    }

    /**
     * Determines if a property value is relevant and should be included in the stored content.
     * Filters out default/irrelevant values that don't add meaningful information.
     */
    private boolean isRelevantValue(String propertyName, Object value, String messageType) {
        // Always include type as it's essential
        if ("type".equals(propertyName)) {
            return true;
        }

        // For tool calls, include state if it's relevant
        if ("state".equals(propertyName)) {
            return "tool".equals(messageType);
        }

        // For string values, exclude empty strings
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }

        // Include all other non-null values
        return true;
    }
}

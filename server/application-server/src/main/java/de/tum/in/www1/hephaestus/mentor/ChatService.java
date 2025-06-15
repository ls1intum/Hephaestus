package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for handling chat streaming and persistence.
 * Integrates with AI SDK message persistence for text parts.
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final IntelligenceServiceWebClient intelligenceServiceWebClient;
    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    EntityManager entityManager;

    public ChatService(
            IntelligenceServiceWebClient intelligenceServiceWebClient,
            ChatThreadRepository chatThreadRepository,
            ChatMessageRepository chatMessageRepository,
            ObjectMapper objectMapper) {
        this.intelligenceServiceWebClient = intelligenceServiceWebClient;
        this.chatThreadRepository = chatThreadRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a chat request by streaming the response and persisting text parts.
     * This implements the vertical slice for text message persistence only.
     */
    public Flux<String> processChat(ChatRequestDTO chatRequest, User user) {
        logger.info("Processing chat request from user: {} with {} messages", 
                   user.getLogin(), chatRequest.messages().size());
        
        try {
            // Find or create thread using the client-provided UUID
            logger.debug("Finding or creating thread with ID: {}", chatRequest.id());
            ChatThread thread = findOrCreateThreadByClientId(chatRequest.id(), user);
            logger.debug("Using thread: {} (title={})", thread.getId(), thread.getTitle());
            
            // Persist user messages from the request
            logger.debug("Persisting latest user message");
            persistLatestUserMessage(chatRequest, thread);
            
            // Create ChatRequest for intelligence service
            logger.debug("Creating ChatRequest for intelligence service");
            ChatRequest intelligenceRequest = new ChatRequest();
            intelligenceRequest.setMessages(chatRequest.messages());
            
            // Stream response and persist assistant messages
            logger.debug("Initiating response streaming from intelligence service");
            return streamAndPersistResponse(intelligenceRequest, thread)
                    .doOnSubscribe(s -> logger.debug("Chat response stream subscription started"))
                    .doOnComplete(() -> logger.debug("Chat response stream completed"))
                    .doOnError(e -> logger.error("Chat response stream error", e))
                    .doOnCancel(() -> logger.debug("Chat response stream was cancelled"));
        } catch (Exception e) {
            logger.error("Unexpected error in processChat", e);
            return Flux.just(
                "3:\"An unexpected error occurred. Please try again.\"\n", 
                "d:{\"finishReason\":\"error\"}\n"
            );
        }
    }

    /**
     * Find or create a chat thread using the client-provided ID.
     * Handles race conditions where multiple requests try to create the same thread.
     */
    private ChatThread findOrCreateThreadByClientId(String threadId, User user) {
        try {
            UUID threadUuid = UUID.fromString(threadId);
            
            // First try to find the thread
            Optional<ChatThread> existingThread = chatThreadRepository.findByIdAndUser(threadUuid, user);
            if (existingThread.isPresent()) {
                return existingThread.get();
            }
            
            // If thread doesn't exist, try to create it
            ChatThread newThread = new ChatThread();
            newThread.setId(threadUuid);
            newThread.setUser(user);
            newThread.setTitle("New chat");
            
            try {
                // Try to save the thread
                return chatThreadRepository.save(newThread);
            } catch (Exception e) {
                // If saving fails (likely due to concurrent creation), try to find it again
                logger.debug("Exception while creating thread with ID {}. Attempting to retrieve it.", threadId);
                return chatThreadRepository.findByIdAndUser(threadUuid, user)
                        .orElseGet(() -> {
                            // If still not found, create a new thread with a different ID
                            logger.warn("Failed to create thread with ID {}. Creating with new ID.", threadId);
                            ChatThread fallbackThread = new ChatThread();
                            fallbackThread.setUser(user);
                            fallbackThread.setTitle("New chat");
                            return chatThreadRepository.save(fallbackThread);
                        });
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid thread UUID format: {}, creating new thread", threadId);
            ChatThread newThread = new ChatThread();
            newThread.setUser(user);
            newThread.setTitle("New chat");
            return chatThreadRepository.save(newThread);
        }
    }

    /**
     * Persist user message from the chat request.
     */
    private void persistLatestUserMessage(ChatRequestDTO chatRequest, ChatThread thread) {
        if (!chatRequest.messages().isEmpty()) {
            Message lastUserMessage = chatRequest.messages().get(chatRequest.messages().size() - 1);
            Optional<Message> parentMessage = chatRequest.messages().size() > 1
                ? Optional.of(chatRequest.messages().get(chatRequest.messages().size() - 2))
                : Optional.empty();
            if ("user".equals(lastUserMessage.getRole())) {
                persistMessage(lastUserMessage, parentMessage, thread, ChatMessage.Role.USER);
            }
        }
    }

    private Flux<String> streamAndPersistResponse(ChatRequest intelligenceRequest, ChatThread thread) {
        logger.debug("Setting up response streaming for thread: {}", thread.getId());
        
        // References to track the assistant message being built
        AtomicReference<ChatMessage> currentAssistantMessage = new AtomicReference<>();
        AtomicReference<StringBuilder> textContentBuilder = new AtomicReference<>(new StringBuilder());
        AtomicReference<Integer> partIndex = new AtomicReference<>(0);

        return intelligenceServiceWebClient.streamChat(intelligenceRequest)
                .doOnSubscribe(s -> logger.debug("Subscribed to intelligence service stream"))
                .doOnNext(chunk -> {
                    logger.debug("Received chunk from intelligence service: {}", chunk);
                    try {
                        processStreamFrame(chunk, thread, currentAssistantMessage, 
                                                textContentBuilder, partIndex);
                    } catch (Exception e) {
                        logger.error("Error processing stream chunk: {}", chunk, e);
                    }
                })
                .doOnComplete(() -> {
                    logger.debug("Intelligence service stream completed");
                    // Finalize the assistant message
                    ChatMessage assistantMessage = currentAssistantMessage.get();
                    if (assistantMessage != null) {
                        // Save final text content if any
                        String finalTextContent = textContentBuilder.get().toString();
                        if (!finalTextContent.isEmpty()) {
                            logger.debug("Saving final text content: {} chars", finalTextContent.length());
                            saveFinalTextPart(assistantMessage, finalTextContent, partIndex.get());
                        } else {
                            logger.debug("No final text content to save");
                        }
                        
                        // Update thread's selected leaf message
                        logger.debug("Finalizing thread with assistant message: {}", assistantMessage.getId());
                        finalizeThread(thread, assistantMessage);
                        
                        logger.info("Completed processing chat for thread: {}", thread.getId());
                    } else {
                        logger.warn("Stream completed but no assistant message was created");
                    }
                })
                .doOnError(error -> {
                    logger.error("Error in chat stream processing", error);
                })
                .onErrorResume(error -> {
                    logger.error("Recovering from stream error", error);
                    return Flux.just(
                        "3:\"An error occurred while processing your request. Please try again.\"\n",
                        "d:{\"finishReason\":\"error\"}\n"
                    );
                });
    }

    private void processStreamFrame(String frame, ChatThread thread, 
                                    AtomicReference<ChatMessage> currentAssistantMessage,
                                    AtomicReference<StringBuilder> textContentBuilder,
                                    AtomicReference<Integer> textPartIndex) {
        try {
            // Parse AI SDK frame format: "type:content"
            if (!frame.contains(":")) {
                logger.warn("Invalid frame format (missing colon separator): {}", frame);
                return;
            }
            
            String frameType = frame.substring(0, frame.indexOf(":"));
            String frameContent = frame.substring(frame.indexOf(":") + 1);
            
            logger.debug("Processing frame type: {} with content length: {}", 
                       frameType, frameContent != null ? frameContent.length() : 0);
            
            // Handle different frame types based on AI SDK protocol
            switch (frameType) {
                case "f": // start_step - initialize assistant message
                    logger.debug("Frame: start_step received");
                    
                    // Extract the messageId from the frame content if available
                    String messageId = null;
                    try {
                        if (frameContent != null && !frameContent.isEmpty()) {
                            JsonNode frameNode = objectMapper.readTree(frameContent);
                            if (frameNode.has("messageId")) {
                                messageId = frameNode.get("messageId").asText();
                                logger.debug("Extracted message ID from frame: {}", messageId);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing messageId from start_step frame: {}", frameContent, e);
                    }
                    
                    // Start of a new reasoning step
                    if (currentAssistantMessage.get() == null) {
                        // Create assistant message with the extracted ID
                        ChatMessage assistantMessage = createAssistantMessage(thread, messageId);
                        currentAssistantMessage.set(assistantMessage);
                        textContentBuilder.set(new StringBuilder());
                        textPartIndex.set(0);
                        logger.debug("Started new assistant message: {}", assistantMessage.getId());
                    } else {
                        // If continuing from a previous step, flush any existing content
                        String currentContent = textContentBuilder.get().toString();
                        if (!currentContent.isEmpty()) {
                            logger.debug("Flushing existing content: {} chars", currentContent.length());
                            saveFinalTextPart(currentAssistantMessage.get(), currentContent, textPartIndex.get());
                            textPartIndex.set(textPartIndex.get() + 1);
                            textContentBuilder.set(new StringBuilder());
                        }
                        logger.debug("Started new step in existing message: {}", currentAssistantMessage.get().getId());
                    }
                    break;
                    
                case "0": // text content
                    logger.debug("Frame: text content received");
                    // Text content streaming - append to current buffer
                    ChatMessage assistantMessage = currentAssistantMessage.get();
                    String textContent = objectMapper.readTree(frameContent).asText();
                    if (assistantMessage != null) {
                        // Remove quotes from JSON string content
                        textContentBuilder.get().append(textContent);
                        logger.debug("Added text content: {} chars to message: {}", 
                                   textContent.length(), assistantMessage.getId());
                    } else {
                        // Text content arrived before start_step, create message first
                        logger.debug("Text content arrived before start_step, creating new message");
                        assistantMessage = createAssistantMessage(thread, null);
                        currentAssistantMessage.set(assistantMessage);
                        textContentBuilder.set(new StringBuilder(textContent));
                        textPartIndex.set(0);
                        logger.debug("Created assistant message for unexpected text content: {}", 
                                   assistantMessage.getId());
                    }
                    break;
                    
                case "e": // finish_step
                    logger.debug("Frame: finish_step received");
                    // End of current reasoning step, potentially with a continuation flag
                    boolean isContinued = false;
                    try {
                        // Parse continuation flag if present
                        if (frameContent != null && !frameContent.isEmpty()) {
                            logger.debug("Parsing finish_step data: {}", frameContent);
                            var finishStepData = objectMapper.readValue(frameContent, Object.class);
                            if (finishStepData instanceof Map) {
                                @SuppressWarnings("unchecked")
                                var dataMap = (Map<String, Object>) finishStepData;
                                isContinued = Boolean.TRUE.equals(dataMap.get("isContinued"));
                                logger.debug("Finish step with isContinued={}", isContinued);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing finish_step data: {}", frameContent, e);
                    }
                    
                    // If not continued, flush content
                    if (!isContinued && currentAssistantMessage.get() != null) {
                        String currentContent = textContentBuilder.get().toString();
                        if (!currentContent.isEmpty()) {
                            logger.debug("Flushing content at finish_step: {} chars", currentContent.length());
                            saveFinalTextPart(currentAssistantMessage.get(), currentContent, textPartIndex.get());
                            textPartIndex.set(textPartIndex.get() + 1);
                            textContentBuilder.set(new StringBuilder());
                        }
                        logger.debug("Finished step, content flushed");
                    } else {
                        logger.debug("Finished step with continuation, content not flushed");
                    }
                    break;
                    
                case "d": // finish_message
                    logger.debug("Frame: finish_message received");
                    // Message is complete - any metadata like finish reason is in the content
                    String finishReason = "stop"; // Default
                    try {
                        if (frameContent != null && !frameContent.isEmpty()) {
                            logger.debug("Parsing finish_message data: {}", frameContent);
                            var finishData = objectMapper.readValue(frameContent, Object.class);
                            if (finishData instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                var dataMap = (java.util.Map<String, Object>) finishData;
                                if (dataMap.containsKey("finishReason")) {
                                    finishReason = String.valueOf(dataMap.get("finishReason"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing finish_message data: {}", frameContent, e);
                    }
                    logger.debug("Message finished with reason: {}", finishReason);
                    break;
                    
                case "1": // reasoning content (same as text but for reasoning parts)
                case "2": // reasoning_signature (metadata about reasoning)
                case "3": // error message
                    logger.debug("Frame: error message received: {}", frameContent);
                    break;
                case "4": // data (any JSON data)
                case "5": // message_annotations
                case "6": // file content
                case "7": // source citation
                case "8": // redacted_reasoning
                case "9": // tool_call_streaming_start
                case "a": // tool_call_delta
                case "b": // tool_call
                case "c": // tool_result
                    // These frame types are not implemented yet but properly logged
                    logger.debug("Received unimplemented frame type: {} with content length: {}", 
                               frameType, frameContent != null ? frameContent.length() : 0);
                    break;
                    
                default:
                    logger.warn("Unknown frame type: {}", frameType);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing frame: {}", frame, e);
        }
    }

    /**
     * Create an assistant message in the database with a specific ID.
     * 
     * @param thread The chat thread to which the message belongs
     * @param messageId The ID to use for the message, or null to generate one
     * @return The saved chat message
     */
    private ChatMessage createAssistantMessage(ChatThread thread, String messageId) {
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setThread(thread);
        
        // Set the ID if provided, otherwise generate one
        if (messageId != null && !messageId.isEmpty()) {
            try {
                UUID id = UUID.fromString(messageId);
                message.setId(id);
                logger.debug("Using provided ID for assistant message: {}", id);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format for message ID: {}, will use generated ID", messageId);
                message.setId(UUID.randomUUID());
            }
        } else {
            message.setId(UUID.randomUUID());
        }
        
        ChatMessage savedMessage = chatMessageRepository.save(message);
        thread.addMessage(savedMessage);
        
        logger.debug("Created assistant message: {}", savedMessage.getId());
        return savedMessage;
    }

    /**
     * Saves the final text part for a message.
     * This is called after the streaming is complete to save the accumulated text.
     */
    @Transactional
    private void saveFinalTextPart(ChatMessage message, String textContent, int partIndex) {
        try {
            ChatMessagePart textPart = new ChatMessagePart();
            textPart.setId(new ChatMessagePartId(message.getId(), partIndex));
            textPart.setType(ChatMessagePart.MessagePartType.TEXT);
            
            // Store text content as JSON string
            JsonNode contentNode = objectMapper.valueToTree(textContent);
            textPart.setContent(contentNode);
            
            // Add to message (don't save individually)
            message.addMessagePart(textPart);
            
            // Save the message which will cascade to the part
            chatMessageRepository.save(message);
            
            logger.debug("Saved text part for message: {} with {} chars", 
                        message.getId(), textContent.length());
        } catch (Exception e) {
            logger.error("Error saving final text part: {}", e.getMessage(), e);
        }
    }

    /**
     * Persist a message and its parts from the AI SDK.
     */
    @Transactional
    private ChatMessage persistMessage(Message apiMessage, Optional<Message> parentApiMessage, ChatThread thread, ChatMessage.Role role) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setThread(thread);
        
        // Set message ID from API message if available, otherwise generate a new one
        if (apiMessage.getId() != null && !apiMessage.getId().isEmpty()) {
            try {
                UUID id = UUID.fromString(apiMessage.getId());
                message.setId(id);
                logger.debug("Using API message ID: {}", id);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format for API message ID: {}, will use generated ID", apiMessage.getId());
                message.setId(UUID.randomUUID());
            }
        } else {
            message.setId(UUID.randomUUID());
        }
        
        // Set parent message if available
        if (parentApiMessage.isPresent()) {
            try {
                UUID parentId = UUID.fromString(parentApiMessage.get().getId());
                ChatMessage parentMessage = chatMessageRepository.findById(parentId).orElse(null);
                if (parentMessage != null) {
                    message.setParentMessage(parentMessage);
                } else {
                    logger.warn("Could not find parent message with ID: {}", parentId);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format for parent message ID: {}", parentApiMessage.get().getId());
            }
        }

        // First save the message to get an ID
        ChatMessage savedMessage = chatMessageRepository.save(message);
        
        // Now prepare message parts using the saved message ID
        if (apiMessage.getParts() != null) {
            int partIndex = 0;
            for (var part : apiMessage.getParts()) {
                if ("text".equals(part.getType()) && part.getText() != null) {
                    try {
                        ChatMessagePart textPart = new ChatMessagePart();
                        textPart.setId(new ChatMessagePartId(savedMessage.getId(), partIndex++));
                        textPart.setType(ChatMessagePart.MessagePartType.TEXT);
                        
                        JsonNode contentNode = objectMapper.valueToTree(part.getText());
                        textPart.setContent(contentNode);
                        savedMessage.addMessagePart(textPart);
                    } catch (Exception e) {
                        logger.error("Error preparing text part for message: {}", e.getMessage(), e);
                    }
                }
            }
        }
        
        // Save the message again with its parts
        savedMessage = chatMessageRepository.save(savedMessage);
        
        // Update thread references
        thread.addMessage(savedMessage);
        thread.setSelectedLeafMessage(savedMessage);
        chatThreadRepository.save(thread);
        
        logger.debug("Persisted {} message: {} with {} parts", 
                    role, savedMessage.getId(), savedMessage.getParts().size());
        return savedMessage;
    }

    /**
     * Updates the thread with the final assistant message and saves it.
     */
    @Transactional
    private void finalizeThread(ChatThread thread, ChatMessage assistantMessage) {
        try {
            thread.setSelectedLeafMessage(assistantMessage);
            chatThreadRepository.save(thread);
            logger.debug("Finalized thread with selected leaf message: {}", assistantMessage.getId());
        } catch (Exception e) {
            logger.error("Error finalizing thread: {}", e.getMessage(), e);
        }
    }
}

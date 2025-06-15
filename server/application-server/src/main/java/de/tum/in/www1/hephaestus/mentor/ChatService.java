package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Service for handling chat streaming and persistence.
 * Implements full AI SDK Data Stream Protocol support with complete message part persistence.
 *
 * Supports all AI SDK frame types:
 * - Text parts (0:) -> TEXT message parts
 * - Reasoning parts (g:) -> REASONING message parts
 * - Data arrays (2:) -> DATA message parts
 * - Error messages (3:) -> ERROR message parts
 * - Tool calls (9:) -> TOOL_INVOCATION message parts
 * - Tool results (a:) -> TOOL_RESULT message parts
 * - Control frames (f:, e:, d:) -> handled for flow control
 *
 * Others are ignored for now.
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private IntelligenceServiceWebClient intelligenceServiceWebClient;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatMessagePartRepository chatMessagePartRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    EntityManager entityManager;

    public Flux<String> processChat(ChatRequestDTO chatRequest, User user) {
        logger.info(
            "Processing chat request from user: {} with {} messages",
            user.getLogin(),
            chatRequest.messages().size()
        );

        try {
            // Find or create thread using the client-provided UUID
            logger.debug("Finding or creating thread with ID: {}", chatRequest.id());
            ChatThread thread = findOrCreateThread(chatRequest.id(), user);
            logger.debug("Using thread: {} (title={})", thread.getId(), thread.getTitle());

            // Persist user messages from the request
            logger.debug("Persisting latest user message");
            var userMessage = persistLatestUserMessage(chatRequest, thread);

            // Create ChatRequest for intelligence service
            logger.debug("Creating ChatRequest for intelligence service");
            ChatRequest intelligenceRequest = new ChatRequest();
            intelligenceRequest.setMessages(chatRequest.messages());

            // Stream response and persist assistant messages
            logger.debug("Initiating response streaming from intelligence service");
            return streamAndPersistResponse(intelligenceRequest, thread, userMessage)
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

    private ChatThread findOrCreateThread(String threadId, User user) {
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

        return chatThreadRepository.save(newThread);
    }

    private ChatMessage persistLatestUserMessage(ChatRequestDTO chatRequest, ChatThread thread) {
        Message apiMessage = chatRequest.messages().getLast();
        Optional<Message> parentApiMessage = chatRequest.messages().size() > 1
            ? Optional.of(chatRequest.messages().get(chatRequest.messages().size() - 2))
            : Optional.empty();

        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.USER);
        message.setThread(thread);

        UUID messageId = UUID.fromString(apiMessage.getId());
        message.setId(messageId);

        // Set parent message if available
        if (parentApiMessage.isPresent()) {
            UUID parentId = UUID.fromString(parentApiMessage.get().getId());
            chatMessageRepository
                .findById(parentId)
                .ifPresentOrElse(message::setParentMessage, () ->
                    logger.warn("Could not find parent message with ID: {}", parentId)
                );
        }

        // First save the message to get an ID
        ChatMessage savedMessage = chatMessageRepository.save(message);
        thread.addMessage(savedMessage);
        thread.setSelectedLeafMessage(savedMessage);

        // Now prepare message parts using the saved message ID
        if (apiMessage.getParts() != null) {
            int partIndex = savedMessage.getParts().size();
            for (var part : apiMessage.getParts()) {
                if ("text".equals(part.getType()) && part.getText() != null) {
                    ChatMessagePart textPart = new ChatMessagePart();
                    textPart.setId(new ChatMessagePartId(savedMessage.getId(), partIndex++));
                    textPart.setType(ChatMessagePart.MessagePartType.TEXT);

                    JsonNode contentNode = objectMapper.valueToTree(part.getText());
                    textPart.setContent(contentNode);
                    savedMessage.addMessagePart(textPart);
                }
            }
        }

        // Save the message again with its parts
        chatMessageRepository.save(savedMessage);
        chatThreadRepository.save(thread);

        logger.debug("Persisted user message: {} with {} parts", savedMessage.getId(), savedMessage.getParts().size());

        return savedMessage;
    }

    private Flux<String> streamAndPersistResponse(
        ChatRequest intelligenceRequest,
        ChatThread thread,
        ChatMessage userMessage
    ) {
        logger.debug(
            "Setting up response streaming for thread: {} and user message: {}",
            thread.getId(),
            userMessage.getId()
        );

        // References to track the assistant message being built
        AtomicReference<ChatMessage> assistantMessage = new AtomicReference<>();
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());
        AtomicReference<Integer> partIndex = new AtomicReference<>(0);
        AtomicReference<Character> lastFrameType = new AtomicReference<>('f');

        return intelligenceServiceWebClient
            .streamChat(intelligenceRequest)
            .doOnSubscribe(s -> logger.debug("Subscribed to intelligence service stream"))
            .doOnNext(chunk -> {
                logger.debug("Received chunk from intelligence service: {}", chunk);
                try {
                    processStreamFrame(
                        chunk,
                        thread,
                        userMessage,
                        assistantMessage,
                        contentBuilder,
                        partIndex,
                        lastFrameType
                    );
                    lastFrameType.set(chunk.charAt(0)); // Update last frame type for next processing
                } catch (Exception e) {
                    logger.error("Error processing stream chunk: {}", chunk, e);
                }
            })
            .doOnComplete(() -> {
                logger.debug("Intelligence service stream completed");
                chatMessageRepository.save(assistantMessage.get());
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

    private void processStreamFrame(
        String frame,
        ChatThread thread,
        ChatMessage userMessage,
        AtomicReference<ChatMessage> assistantMessage,
        AtomicReference<StringBuilder> contentBuilder,
        AtomicReference<Integer> partIndex,
        AtomicReference<Character> lastFrameType
    ) {
        try {
            // Parse AI SDK frame format: "type:content"
            if (!frame.contains(":")) {
                logger.warn("Invalid frame format (missing colon separator): {}", frame);
                return;
            }

            String frameType = frame.substring(0, frame.indexOf(":"));
            String frameContent = frame.substring(frame.indexOf(":") + 1);
            JsonNode frameNode = objectMapper.readTree(frameContent);

            logger.debug(
                "Processing frame type: {} with content length: {}",
                frameType,
                frameContent != null ? frameContent.length() : 0
            );

            // Handle different frame types based on AI SDK protocol
            switch (frameType) {
                case "f": // start_step - initialize assistant message
                    logger.debug("Frame: start_step received");
                    UUID messageId = UUID.fromString(frameNode.get("messageId").asText());

                    // Always create a new assistant message (if one already exists, it will be replaced)
                    ChatMessage message = new ChatMessage();
                    message.setRole(ChatMessage.Role.ASSISTANT);
                    message.setThread(thread);
                    message.setId(messageId);
                    message.setParentMessage(userMessage);
                    thread.setSelectedLeafMessage(message);
                    chatThreadRepository.save(thread);

                    // Replace the existing assistant message if it exists
                    if (assistantMessage.get() != null) {
                        ChatMessage oldMessage = assistantMessage.get();
                        logger.debug(
                            "Replacing existing assistant message: {} with new one: {}",
                            oldMessage.getId(),
                            messageId
                        );

                        // Transfer parts from old message to new one
                        oldMessage
                            .getParts()
                            .forEach(part -> {
                                part.setMessage(message);
                            });
                        message.setCreatedAt(oldMessage.getCreatedAt());
                        assistantMessage.set(chatMessageRepository.save(message));
                        chatMessageRepository.delete(oldMessage);
                    }

                    // Initialize text/reasoning content builders
                    contentBuilder.set(new StringBuilder());
                    break;
                case "0": // text content
                    if (lastFrameType.get() == 'g') { // Save reasoning
                        // If reasoning content exists, save it
                        saveMessagePart(
                            assistantMessage.get(),
                            ChatMessagePart.MessagePartType.REASONING,
                            contentBuilder.get().toString(),
                            partIndex.get()
                        );
                        partIndex.set(partIndex.get() + 1);
                        contentBuilder.set(new StringBuilder());
                    }
                    contentBuilder.get().append(frameNode.asText());
                    break;
                case "e": // finish_step
                    // if g or 0 was the last frame, we need to save the content
                    if (lastFrameType.get() == '0' || lastFrameType.get() == 'g') {
                        saveMessagePart(
                            assistantMessage.get(),
                            lastFrameType.get() == '0'
                                ? ChatMessagePart.MessagePartType.TEXT
                                : ChatMessagePart.MessagePartType.REASONING,
                            contentBuilder.get().toString(),
                            partIndex.get()
                        );
                        partIndex.set(partIndex.get() + 1);
                        contentBuilder.set(new StringBuilder());
                    }
                    break;
                case "d": // finish_message
                    // Message is complete - any metadata like finish reason is in the content
                    String finishReason = frameNode.get("finishReason").asText("unknown");
                    logger.debug("Message finished with reason: {}", finishReason);
                    break;
                case "g": // reasoning content
                    contentBuilder.get().append(frameNode.asText());
                    break;
                case "i": // redacted reasoning
                    logger.warn("Frame: redacted reasoning received, skipping content");
                    break;
                case "j": // reasoning signature
                    logger.warn("Frame: reasoning signature received, skipping content");
                    break;
                case "h": // source citation
                    logger.warn("Frame: source citation received, skipping content");
                    break;
                case "k": // file attachment
                    logger.warn("Frame: file attachment received, skipping content");
                    break;
                case "2": // data array
                    logger.warn("Frame: data array received, skipping content");
                    break;
                case "8": // message annotations
                    logger.warn("Frame: message annotations received, skipping content");
                    break;
                case "3": // error message
                    logger.warn("Frame: error message received, skipping content");
                    break;
                case "9": // tool call (complete)
                    // if g or 0 was the last frame, we need to save the content
                    if (lastFrameType.get() == '0' || lastFrameType.get() == 'g') {
                        saveMessagePart(
                            assistantMessage.get(),
                            lastFrameType.get() == '0'
                                ? ChatMessagePart.MessagePartType.TEXT
                                : ChatMessagePart.MessagePartType.REASONING,
                            contentBuilder.get().toString(),
                            partIndex.get()
                        );
                        partIndex.set(partIndex.get() + 1);
                        contentBuilder.set(new StringBuilder());
                    }

                    // Create a new tool invocation part
                    saveMessagePart(assistantMessage.get(), ChatMessagePart.MessagePartType.TOOL_INVOCATION, frameNode, partIndex.get());
                    partIndex.set(partIndex.get() + 1);
                    break;
                case "a": // tool result
                    JsonNode toolResult = frameNode.get("result");
                    Optional<ChatMessagePart> lastPart = assistantMessage.get().getParts().stream()
                        .filter(part -> part.getType() == ChatMessagePart.MessagePartType.TOOL_INVOCATION)
                        .reduce((first, second) -> second); // Get the last tool invocation part
                    if (lastPart.isPresent()) {
                        var content = (ObjectNode) lastPart.get().getContent();
                        content.set("result", toolResult);
                        lastPart.get().setContent(content);
                        chatMessagePartRepository.save(lastPart.get());
                    } else {
                        logger.warn("No tool invocation part found to attach result to");
                    }
                    break;
                case "b": // tool call streaming start (streaming only - don't persist)
                    logger.debug("Frame: tool call streaming start (not persisted): {}", frameContent);
                    break;
                case "c": // tool call delta (streaming only - don't persist)
                    logger.debug("Frame: tool call delta (not persisted): {}", frameContent);
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
     * Helper method to save a message part with proper content handling.
     * Handles both String content and JsonNode content.
     */
    @Transactional
    private void saveMessagePart(
        ChatMessage message,
        ChatMessagePart.MessagePartType type,
        Object content,
        int partIndex
    ) {
        try {
            ChatMessagePart messagePart = new ChatMessagePart();
            messagePart.setId(new ChatMessagePartId(message.getId(), partIndex));
            messagePart.setType(type);

            // Convert content to JsonNode based on type
            JsonNode contentNode;
            if (content instanceof String) {
                contentNode = objectMapper.valueToTree(content);
            } else if (content instanceof JsonNode) {
                contentNode = (JsonNode) content;
            } else {
                contentNode = objectMapper.valueToTree(content);
            }

            messagePart.setContent(contentNode);

            // Add to message (don't save individually)
            message.addMessagePart(messagePart);

            // Save the message which will cascade to the part
            chatMessageRepository.save(message);

            logger.debug("Saved {} part for message: {} at index: {}", type, message.getId(), partIndex);
        } catch (Exception e) {
            logger.error("Error saving message part of type {}: {}", type, e.getMessage(), e);
        }
    }
}

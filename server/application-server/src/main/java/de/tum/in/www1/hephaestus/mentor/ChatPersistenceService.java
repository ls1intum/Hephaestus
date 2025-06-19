package de.tum.in.www1.hephaestus.mentor;

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
     * Creates a new stateful processor for a specific chat stream.
     * Each stream gets its own isolated state and database transaction context.
     */
    public StreamPartProcessor createProcessor(User user, ChatThread thread, ChatMessage parentMessage) {
        return new ChatStreamProcessor(user, thread, parentMessage);
    }

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
     * Stateful processor for a single chat stream.
     * Encapsulates all state and synchronization for one conversation.
     */
    private class ChatStreamProcessor implements StreamPartProcessor {

        @SuppressWarnings("unused") // Reserved for future use
        private final User user;

        private final ChatThread thread;
        private final ChatMessage parentMessage;
        private final ReentrantLock lock = new ReentrantLock();

        // Stream state - isolated per processor instance
        private ChatMessage currentMessage;
        private final StringBuilder textContent = new StringBuilder();
        private final AtomicInteger partOrder = new AtomicInteger(0);

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
        public void onToolInputStart(StreamToolInputStartPart toolStart) {
            lock.lock();
            try {
                createMessagePart(ChatMessagePart.PartType.TOOL, toolStart);
                logger.debug(
                    "Tool execution started: toolName={}, callId={}",
                    toolStart.getToolName(),
                    toolStart.getToolCallId()
                );
            } catch (Exception e) {
                logger.error("Failed to process tool start: {}", e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onToolInputAvailable(StreamToolInputAvailablePart toolInput) {
            lock.lock();
            try {
                createMessagePart(ChatMessagePart.PartType.TOOL, toolInput);
                logger.debug(
                    "Tool input available: toolName={}, callId={}",
                    toolInput.getToolName(),
                    toolInput.getToolCallId()
                );
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
                createMessagePart(ChatMessagePart.PartType.TOOL, toolOutput);
                logger.debug("Tool output available: callId={}", toolOutput.getToolCallId());
            } catch (Exception e) {
                logger.error("Failed to process tool output: {}", e.getMessage(), e);
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

                // Create the final combined text part if we have accumulated text
                if (textContent.length() > 0) {
                    ChatMessagePart textPart = new ChatMessagePart();
                    ChatMessagePartId partId = new ChatMessagePartId(
                        currentMessage.getId(),
                        0 // Single text part gets order 0
                    );
                    textPart.setId(partId);
                    textPart.setMessage(currentMessage);
                    textPart.setType(ChatMessagePart.PartType.TEXT);
                    textPart.setContent(objectMapper.valueToTree(textContent.toString()));

                    chatMessagePartRepository.save(textPart);
                    currentMessage.getParts().add(textPart);
                }

                logger.debug(
                    "Message stream completed: messageId={}, finalContentLength={}",
                    currentMessage.getId(),
                    textContent.length()
                );

                // Final save
                chatMessageRepository.save(currentMessage);
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

                // Special handling for text parts to store just the text content
                if (type == ChatMessagePart.PartType.TEXT && streamPart instanceof StreamTextPart) {
                    StreamTextPart textPart = (StreamTextPart) streamPart;
                    part.setContent(objectMapper.valueToTree(textPart.getText()));
                } else {
                    part.setContent(objectMapper.valueToTree(streamPart));
                }

                chatMessagePartRepository.save(part);
            } catch (Exception e) {
                logger.error("Failed to create message part: {}", e.getMessage(), e);
                throw new RuntimeException("Message part persistence failed", e);
            }
        }

        // Implement remaining StreamPartProcessor methods...
        @Override
        public void onStepStart(StreamStepStartPart stepStart) {
            createMessagePart(ChatMessagePart.PartType.STEP_START, stepStart);
        }

        @Override
        public void onStepFinish(StreamStepFinishPart stepFinish) {
            // Create appropriate part type when available
        }

        @Override
        public void onToolInputDelta(StreamToolInputDeltaPart toolDelta) {
            createMessagePart(ChatMessagePart.PartType.TOOL, toolDelta);
        }

        @Override
        public void onReasoningChunk(StreamReasoningPart reasoning) {
            createMessagePart(ChatMessagePart.PartType.REASONING, reasoning);
        }

        @Override
        public void onReasoningFinish(StreamReasoningFinishPart reasoningFinish) {
            createMessagePart(ChatMessagePart.PartType.REASONING, reasoningFinish);
        }

        @Override
        public void onSourceUrl(StreamSourceUrlPart sourceUrl) {
            createMessagePart(ChatMessagePart.PartType.SOURCE_URL, sourceUrl);
        }

        @Override
        public void onSourceDocument(StreamSourceDocumentPart sourceDocument) {
            createMessagePart(ChatMessagePart.PartType.SOURCE_DOCUMENT, sourceDocument);
        }

        @Override
        public void onFile(StreamFilePart file) {
            createMessagePart(ChatMessagePart.PartType.FILE, file);
        }

        @Override
        public void onDataPart(StreamDataPart data) {
            createMessagePart(ChatMessagePart.PartType.DATA, data);
        }

        @Override
        public void onMessageMetadata(StreamMessageMetadataPart messageMetadata) {
            // Update message metadata directly
            if (currentMessage != null) {
                currentMessage.setMetadata(objectMapper.valueToTree(messageMetadata));
                chatMessageRepository.save(currentMessage);
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

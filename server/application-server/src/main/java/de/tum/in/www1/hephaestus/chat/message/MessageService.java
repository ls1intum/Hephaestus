package de.tum.in.www1.hephaestus.chat.message;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.chat.Session;
import de.tum.in.www1.hephaestus.chat.SessionDTO;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DefaultApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatResponse;
import de.tum.in.www1.hephaestus.chat.SessionRepository;

/**
 * Service for managing messages.
 */
@Service
public class MessageService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DefaultApi sessionApiClient;

    public MessageService(SessionRepository sessionRepository, MessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sessionApiClient = new DefaultApi();
    }

    /**
     * Sends a message, saves it, and generates a bot response.
     */
    public MessageDTO sendMessage(MessageDTO messageDTO) {
        Session session = sessionRepository.findById(messageDTO.session().id())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        Message userMessage = new Message(ZonedDateTime.now(), MessageSender.USER, messageDTO.content(), session);
        messageRepository.save(userMessage);

        String systemResponse = generateResponse(messageDTO.session(), messageDTO.content());

        Message systemMessage = new Message(ZonedDateTime.now(), MessageSender.SYSTEM, systemResponse, session);
        messageRepository.save(systemMessage);

        return new MessageDTO(systemMessage.getId(), systemMessage.getSentAt(), systemMessage.getSender(),
                systemMessage.getContent(), SessionDTO.fromSession(systemMessage.getSession()));
    }

    /**
     * Calls the Python FastAPI service to generate a bot response.
     */
    private String generateResponse(SessionDTO session, String messageContent) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setSessionId(session.id().toString());
        chatRequest.setMessageContent(messageContent);

        try {
            ChatResponse chatResponse = sessionApiClient.chatChatPost(chatRequest);
            return chatResponse.getMessageContent();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with intelligence service: " + e.getMessage(), e);
        }
    }
}
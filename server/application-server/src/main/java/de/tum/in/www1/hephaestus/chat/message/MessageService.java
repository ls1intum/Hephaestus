package de.tum.in.www1.hephaestus.chat.message;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.chat.session.Session;
import de.tum.in.www1.hephaestus.chat.session.SessionRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DefaultApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatResponse;

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

    public List<MessageDTO> getMessagesBySessionId(Long sessionId) {
        return messageRepository.findBySessionId(sessionId).stream()
                .map(message -> new MessageDTO(message.getId(), message.getSentAt(), message.getSender(),
                        message.getContent(), message.getSession().getId()))
                .toList();
    }

    public MessageDTO sendMessage(MessageDTO messageDTO) {
        Session session = sessionRepository.findById(messageDTO.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        Message userMessage = new Message(messageDTO.sentAt(), MessageSender.USER, messageDTO.content(), session);
        messageRepository.save(userMessage);

        String systemResponse = generateResponse(messageDTO.sessionId(), messageDTO.content());

        Message systemMessage = new Message(ZonedDateTime.now(), MessageSender.SYSTEM, systemResponse, session);
        messageRepository.save(systemMessage);

        return new MessageDTO(systemMessage.getId(), systemMessage.getSentAt(), systemMessage.getSender(),
                systemMessage.getContent(), systemMessage.getSession().getId());
    }


    private String generateResponse(Long session_id, String messageContent) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setSessionId(session_id.toString());
        chatRequest.setMessageContent(messageContent);

        try {
            ChatResponse chatResponse = sessionApiClient.chatChatPost(chatRequest);
            return chatResponse.getMessageContent();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with intelligence service: " + e.getMessage(), e);
        }
    }
}
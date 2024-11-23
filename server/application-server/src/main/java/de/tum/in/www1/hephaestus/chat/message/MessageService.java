package de.tum.in.www1.hephaestus.chat.message;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.chat.session.Session;
import de.tum.in.www1.hephaestus.chat.session.SessionRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DefaultApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatResponse;

@Service
public class MessageService {

    private DefaultApi sessionApiClient;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private MessageRepository messageRepository;

    public MessageService() {
        this.sessionApiClient = new DefaultApi();
    }

    public List<MessageDTO> getMessagesBySessionId(Long sessionId) {
        return messageRepository.findBySessionId(sessionId).stream()
                .map(message -> new MessageDTO(message.getId(), message.getSentAt(), message.getSender(),
                        message.getContent(), message.getSession().getId()))
                .toList();
    }

    public MessageDTO sendMessage(String content, Long sessionId) {
        Optional<Session>  session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return null;
        }

        Message userMessage = new Message(MessageSender.USER, content, session.get());
        
        // String systemResponse = generateResponse(sessionId, content);

        // Message systemMessage = new Message(OffsetDateTime.now(), MessageSender.SYSTEM, systemResponse, session);
        // messageRepository.saveAndFlush(systemMessage);

        // return new MessageDTO(systemMessage.getId(), systemMessage.getSentAt(), systemMessage.getSender(),
        //         systemMessage.getContent(), systemMessage.getSession().getId());
        return MessageDTO.fromMessage(messageRepository.save(userMessage));
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
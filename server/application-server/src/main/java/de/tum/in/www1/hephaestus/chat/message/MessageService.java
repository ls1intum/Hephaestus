package de.tum.in.www1.hephaestus.chat.message;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.chat.session.Session;
import de.tum.in.www1.hephaestus.chat.session.SessionRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
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
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("http://127.0.0.1:8000");
        this.sessionApiClient = new DefaultApi(apiClient);
    }

    public List<MessageDTO> getMessagesBySessionId(Long sessionId) {
        return messageRepository.findBySessionId(sessionId).stream()
                .map(message -> new MessageDTO(message.getId(), message.getSentAt(), message.getSender(),
                        message.getContent(), message.getSession().getId()))
                .toList();
    }

    public MessageDTO sendMessage(String content, Long sessionId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty() || content == null) {
            return null;
        }

        Message userMessage = new Message();
        userMessage.setSender(MessageSender.USER);
        userMessage.setContent(content);
        userMessage.setSession(session.get());

        Message savedUserMessage = messageRepository.save(userMessage);
        session.get().getMessages().add(savedUserMessage);
        sessionRepository.save(session.get());

        String systemResponse = generateResponse(sessionId, content);

        // prevent saving empty system messages
        if (systemResponse == null) {
            return MessageDTO.fromMessage(savedUserMessage);
        }

        Message systemMessage = new Message();
        systemMessage.setSender(MessageSender.SYSTEM);
        systemMessage.setContent(systemResponse);
        systemMessage.setSession(session.get());

        Message savedSystemMessage = messageRepository.save(systemMessage);
        session.get().getMessages().add(savedSystemMessage);
        sessionRepository.save(session.get());

        return MessageDTO.fromMessage(savedSystemMessage);

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
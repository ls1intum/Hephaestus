package de.tum.in.www1.hephaestus.chat.message;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.chat.session.Session;
import de.tum.in.www1.hephaestus.chat.session.SessionRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DefaultApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatMessage;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatResponse;

@Service
public class MessageService {

    private DefaultApi sessionApiClient;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private MessageRepository messageRepository;

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    public MessageService() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("http://127.0.0.1:8000");
        this.sessionApiClient = new DefaultApi(apiClient);
    }

    public List<MessageDTO> getMessagesBySessionId(Long sessionId) {
        return messageRepository.findBySessionId(sessionId).stream()
                .map(message -> MessageDTO.fromMessage(message))
                .toList();
    }

    public MessageDTO sendMessage(String content, Long sessionId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty() || content == null) {
            return null;
        }
        Session currentSession = session.get();

        Message userMessage = new Message();
        userMessage.setSender(MessageSender.USER);
        userMessage.setContent(content);
        userMessage.setSession(currentSession);

        Message savedUserMessage = messageRepository.save(userMessage);
        currentSession.getMessages().add(savedUserMessage);
        sessionRepository.save(currentSession);

        String systemResponse = generateResponse(sessionId, content);

        // prevent saving empty system messages if the intelligence service is down
        if (systemResponse == null) {
            return MessageDTO.fromMessage(savedUserMessage);
        }

        Message systemMessage = new Message();
        systemMessage.setSender(MessageSender.LLM);
        systemMessage.setContent(systemResponse);
        systemMessage.setSession(currentSession);

        Message savedSystemMessage = messageRepository.save(systemMessage);
        currentSession.getMessages().add(savedSystemMessage);
        sessionRepository.save(currentSession);

        return MessageDTO.fromMessage(savedSystemMessage);

    }

    private String generateResponse(Long session_id, String messageContent) {
        List<Message> messages = messageRepository.findBySessionId(session_id);

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessageHistory(messages.stream()
                .<ChatMessage>map(message -> new ChatMessage().content(message.getContent()).sender(message.getSender().toString()))
                .toList());
        try {
            ChatResponse chatResponse = sessionApiClient.chatChatPost(chatRequest);
            return chatResponse.getResponce();
            
        } catch (Exception e) {
            logger.error("Failed to generate response for message: {}", e.getMessage());
            return null;
        }
    }
}
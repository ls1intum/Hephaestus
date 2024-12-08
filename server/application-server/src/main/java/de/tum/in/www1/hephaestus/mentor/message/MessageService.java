package de.tum.in.www1.hephaestus.mentor.message;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.config.IntelligenceServiceConfig.IntelligenceServiceApi;
import de.tum.in.www1.hephaestus.mentor.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.mentor.session.Session;
import de.tum.in.www1.hephaestus.mentor.session.SessionRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISChatMessage;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISChatResponse;

@Service
public class MessageService {

    @Autowired
    private IntelligenceServiceApi intelligenceServiceApi;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private MessageRepository messageRepository;

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

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
        systemMessage.setSender(MessageSender.MENTOR);
        systemMessage.setContent(systemResponse);
        systemMessage.setSession(currentSession);

        Message savedSystemMessage = messageRepository.save(systemMessage);
        currentSession.getMessages().add(savedSystemMessage);
        sessionRepository.save(currentSession);

        return MessageDTO.fromMessage(savedSystemMessage);
    }

    private String generateResponse(Long sessionId, String messageContent) {
        List<Message> messages = messageRepository.findBySessionId(sessionId);

        ISChatRequest chatRequest = new ISChatRequest();
        chatRequest.setMessageHistory(messages.stream()
                .<ISChatMessage>map(message -> new ISChatMessage().content(message.getContent()).sender(message.getSender().toString()))
                .toList());
        try {
            ISChatResponse chatResponse = intelligenceServiceApi.chatChatPost(chatRequest);
            return chatResponse.getResponce();
            
        } catch (Exception e) {
            logger.error("Failed to generate response for message: {}", e.getMessage());
            return null;
        }
    }
}
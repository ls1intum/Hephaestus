package de.tum.in.www1.hephaestus.mentor.message;

import de.tum.in.www1.hephaestus.config.IntelligenceServiceConfig.IntelligenceServiceApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorResponse;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorStartRequest;
import de.tum.in.www1.hephaestus.mentor.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.mentor.session.Session;
import de.tum.in.www1.hephaestus.mentor.session.SessionRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        return messageRepository
            .findBySessionIdOrderBySentAtAsc(sessionId)
            .stream()
            .map(message -> MessageDTO.fromMessage(message))
            .toList();
    }

    public MessageDTO sendMessage(String content, Long sessionId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty() || content == null) {
            return null;
        }
        Session currentSession = session.get();
        if (currentSession.isClosed()) {
            return null;
        }

        Message userMessage = new Message();
        userMessage.setSender(MessageSender.USER);
        userMessage.setContent(content);
        userMessage.setSession(currentSession);

        Message savedUserMessage = messageRepository.save(userMessage);
        currentSession.getMessages().add(savedUserMessage);
        sessionRepository.save(currentSession);

        try {
            MentorRequest mentorRequest = new MentorRequest();
            mentorRequest.setContent(content);
            mentorRequest.setSessionId(String.valueOf(sessionId));
            MentorResponse mentorMessage = intelligenceServiceApi.generateMentorPost(mentorRequest);
            String mentorResponse = mentorMessage.getContent();
            Message savedMentorMessage = createMentorMessage(currentSession, mentorResponse);
            // update session status if mentor finished the conversation
            if (mentorMessage.getClosed()) {
                currentSession.setClosed(true);
                sessionRepository.save(currentSession);
            }

            return MessageDTO.fromMessage(savedMentorMessage);
        } catch (Exception e) {
            logger.error("Failed to generate response for message: {}", content);
            return null;
        }
    }

    public void sendFirstMessage(Session session, String previousSessionId, String devProgress) {
        try {
            MentorStartRequest mentorStartRequest = new MentorStartRequest();
            mentorStartRequest.setPreviousSessionId(previousSessionId);
            mentorStartRequest.setSessionId(String.valueOf(session.getId()));
            mentorStartRequest.setDevProgress(devProgress);
            mentorStartRequest.setUserId(String.valueOf(session.getUser().getId()));
            MentorResponse mentorMessage = intelligenceServiceApi.startMentorStartPost(mentorStartRequest);
            createMentorMessage(session, mentorMessage.getContent());
        } catch (Exception e) {
            logger.error("Failed to generate response during session start");
        }
    }

    private Message createMentorMessage(Session currentSession, String systemResponse) {
        Message systemMessage = new Message();
        systemMessage.setSender(MessageSender.MENTOR);
        systemMessage.setContent(systemResponse);
        systemMessage.setSession(currentSession);
        Message savedSystemMessage = messageRepository.save(systemMessage);
        currentSession.getMessages().add(savedSystemMessage);
        sessionRepository.save(currentSession);
        return savedSystemMessage;
    }
}

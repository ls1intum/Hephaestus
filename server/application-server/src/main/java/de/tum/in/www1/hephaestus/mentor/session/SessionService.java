package de.tum.in.www1.hephaestus.mentor.session;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.message.MessageService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private MessageService messageService;

    public void checkAccessElseThrow(User user, Session session) {
        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Session", session.getId());
        }
    }

    public List<SessionDTO> findAllSessionsByUser(User user) {
        List<Session> sessions = sessionRepository.findByUser(user);
        return sessions.stream().map(SessionDTO::fromSession).toList();
    }

    public Optional<SessionDTO> findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId).map(SessionDTO::fromSession);
    }

    public SessionDTO createSession(User user) {
        String previous_session_id = sessionRepository
                .findFirstByUserOrderByCreatedAtDesc(user)
                .map(Session::getId)
                .map(String::valueOf)
                .orElse("");
        // close the previous session if it exists to prevent multiple open sessions
        if (previous_session_id != "") {
            Session previous_session = sessionRepository
                    .findFirstByUserOrderByCreatedAtDesc(user).get();
            previous_session.setClosed(true);
            System.out.println("Closing previous session with id: " + previous_session.getId());
            sessionRepository.save(previous_session);
        }

        // create a new session
        Session session = new Session();
        session.setUser(user);
        Session savedSession = sessionRepository.save(session);
        messageService.sendFirstMessage(session, previous_session_id);
        return SessionDTO.fromSession(savedSession);
    }
}

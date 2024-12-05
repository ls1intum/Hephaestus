package de.tum.in.www1.hephaestus.mentor.session;

import java.util.Optional;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Service
public class SessionService {
    
    @Autowired
    private SessionRepository sessionRepository;

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
        Session session = new Session();
        session.setUser(user);

        return SessionDTO.fromSession(sessionRepository.save(session));
    }
}
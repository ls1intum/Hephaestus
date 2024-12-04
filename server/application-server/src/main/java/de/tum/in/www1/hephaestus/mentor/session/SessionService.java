package de.tum.in.www1.hephaestus.mentor.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.Optional;
import java.util.List;

@Service
public class SessionService {
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;

    public List<SessionDTO> findAllSessionsByUser(String login) {
        List<Session> sessions = sessionRepository.findByUserLogin(login);
        return sessions.stream().map(SessionDTO::fromSession).toList();
    }

    public Optional<SessionDTO> findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId).map(SessionDTO::fromSession);
    }

    public SessionDTO createSession(String login) {
        var user = userRepository.findByLogin(login);
        if (user.isEmpty()) {
            return null;
        }

        Session session = new Session();
        session.setUser(user.get());

        return SessionDTO.fromSession(sessionRepository.save(session));
    }
}
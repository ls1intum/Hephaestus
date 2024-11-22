package de.tum.in.www1.hephaestus.chat.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<SessionDTO> findAllSessionsByUser(String login) {
        Optional<List<Session>> sessions = sessionRepository.findByUserLogin(login);
        if (sessions.isEmpty()) {
            return List.of();
        }
        return sessions.get().stream().map(SessionDTO::fromSession).toList();
    }

    public Optional<SessionDTO> findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId).map(SessionDTO::fromSession);
    }

    public SessionDTO createSession(String login) {
        Session session = new Session();
        var user = userRepository.findByLogin(login);
        if (user.isPresent()){
            session.setUser(user.get());
        }

        return SessionDTO.fromSession(sessionRepository.saveAndFlush(session));
    }
}
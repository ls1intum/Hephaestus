package de.tum.in.www1.hephaestus.chat;

import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Service for managing sessions.
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Creates a new session for the given user.
     * 
     * @param user The user the session belongs to
     * @return The created session
     */
    public SessionDTO createSession(User user) {
        Session session = new Session();
        session.setUser(user);
        session.setCreatedAt(OffsetDateTime.now());
        return new SessionDTO(sessionRepository.save(session));
    }

    /**
     * Finds a session by its ID.
     * 
     * @param sessionId The ID of the session to find
     * @return The session entity if found, otherwise throws an exception
     */
    public Optional<SessionDTO> findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId).map(SessionDTO::new);
    }

}
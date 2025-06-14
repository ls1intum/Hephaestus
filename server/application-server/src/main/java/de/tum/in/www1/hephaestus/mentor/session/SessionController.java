package de.tum.in.www1.hephaestus.mentor.session;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mentor/sessions")
public class SessionController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionDTO>> getAllSessions() {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SessionDTO> sessions = sessionService.findAllSessionsByUserByCreatedAtDesc(user.get());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/last")
    public ResponseEntity<SessionDTO> getLastSession() {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<SessionDTO> sessions = sessionService.findAllSessionsByUserByCreatedAtDesc(user.get());
        if (sessions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(sessions.getFirst());
    }

    @PostMapping
    public ResponseEntity<SessionDTO> createNewSession() {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SessionDTO session = sessionService.createSession(user.get());
        return ResponseEntity.ok(session);
    }
}

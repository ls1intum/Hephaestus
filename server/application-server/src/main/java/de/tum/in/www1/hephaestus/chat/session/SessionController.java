package de.tum.in.www1.hephaestus.chat.session;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionDTO> createSession(@RequestBody String login) {
        SessionDTO session = sessionService.createSession(login);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionDTO> getSession(@PathVariable Long sessionId) {
        Optional<SessionDTO> session = sessionService.findSessionById(sessionId);
        return ResponseEntity.ok(session.get());
    }
}
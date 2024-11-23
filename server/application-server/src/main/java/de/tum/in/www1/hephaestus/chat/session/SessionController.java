package de.tum.in.www1.hephaestus.chat.session;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionDTO>> getSessions(@RequestParam String login) {
        List<SessionDTO> sessions = sessionService.findAllSessionsByUser(login);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping
    public ResponseEntity<SessionDTO> createSession(@RequestBody String login) {
        SessionDTO session = sessionService.createSession(login);
        return ResponseEntity.ok(session);
    }
}
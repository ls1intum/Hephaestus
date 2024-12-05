package de.tum.in.www1.hephaestus.mentor.message;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.session.SessionRepository;
import de.tum.in.www1.hephaestus.mentor.session.SessionService;

@RestController
@RequestMapping("/mentor/sessions")
public class MessageController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MessageService messageService;

    @GetMapping("/{sessionId}")
    public ResponseEntity<List<MessageDTO>> getMessages(@PathVariable Long sessionId) {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.checkAccessElseThrow(user.get(), session.get());

        List<MessageDTO> messages = messageService.getMessagesBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<MessageDTO> createMessage(@RequestBody String message, @PathVariable Long sessionId) {
        var user = userRepository.getCurrentUser();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.checkAccessElseThrow(user.get(), session.get());

        MessageDTO createdMessage = messageService.sendMessage(message, sessionId);
        return ResponseEntity.ok(createdMessage);
    }
}
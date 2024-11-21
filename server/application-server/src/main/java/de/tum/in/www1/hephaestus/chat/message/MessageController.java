package de.tum.in.www1.hephaestus.chat.message;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<List<MessageDTO>> getMessages(@PathVariable Long sessionId) {
        List<MessageDTO> messages = messageService.getMessagesBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<MessageDTO> createMessage(@RequestBody String message, @PathVariable Long sessionId) {
        MessageDTO createdMessage = messageService.sendMessage(message, sessionId);
        return ResponseEntity.ok(createdMessage);
    }
}
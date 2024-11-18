package de.tum.in.www1.hephaestus.chat.message;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageDTO> sendMessage(@RequestBody MessageDTO messageDTO) {
        MessageDTO savedMessage = messageService.sendMessage(messageDTO);
        return ResponseEntity.ok(savedMessage);
    }
}
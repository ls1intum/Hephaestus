package de.tum.in.www1.hephaestus.chat;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import de.tum.in.www1.hephaestus.gitprovider.user.User;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatDTO> createChat(@RequestBody User user) {
        return ResponseEntity.ok(chatService.createChat(user));
    }

    @GetMapping
    public ResponseEntity<ChatDTO> getChat(@PathVariable Long chatId) {
        return ResponseEntity.ok(chatService.findChatById(chatId).get());
    }
}
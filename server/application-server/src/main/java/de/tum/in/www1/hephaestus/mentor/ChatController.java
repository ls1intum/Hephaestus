package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import java.util.UUID;

@RestController
@RequestMapping("/mentor/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final IntelligenceServiceWebClient intelligenceServiceWebClient;
    
    public ChatController(IntelligenceServiceWebClient intelligenceServiceWebClient) {
        this.intelligenceServiceWebClient = intelligenceServiceWebClient;
    }
    
    @Hidden
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO mentorRequest) {
        logger.info("Processing chat request with {} messages", mentorRequest.messages().size());

        ChatRequest intelligenceRequest = new ChatRequest();
        intelligenceRequest.setMessages(mentorRequest.messages().stream()
            .map(message -> {
                if (message.getId() == null || message.getId().isEmpty()) {
                    message.setId(UUID.randomUUID().toString());
                }
                return message;
            })
            .toList());

        return intelligenceServiceWebClient.streamChat(intelligenceRequest);
    }
}

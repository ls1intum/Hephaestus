package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Profile("test")
class StubChatFrameHolder {
    public volatile List<String> frames = List.of();
}

@Profile("test")
@RestController
@RequestMapping("/_stub/intelligence-service")
public class StubIntelligenceServiceChatController {

    private final StubChatFrameHolder holder;

    public StubIntelligenceServiceChatController(StubChatFrameHolder holder) {
        this.holder = holder;
    }

    @PostMapping(path = "/mentor/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest ignored) {
        return Flux.fromIterable(holder.frames)
                   .map(frame -> frame + "\n");
    }
}

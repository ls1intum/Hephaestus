package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Profile("test")
class MockChatFrameHolder {
    public volatile List<String> frames = List.of();
}

/**
 * Mock implementation of IntelligenceServiceWebClient for testing.
 */
@Component
@Primary
@Profile("test")
public class MockIntelligenceServiceWebClient implements IntelligenceServiceWebClient {

    @Autowired
    private MockChatFrameHolder mockFrameHolder;

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        return Flux.fromIterable(mockFrameHolder.frames)
            .map(chunk -> chunk + "\n");
    }
}

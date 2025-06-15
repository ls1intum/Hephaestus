package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
class MockChatFrameHolder {
    private final ConcurrentHashMap<String, List<String>> messageFrames = new ConcurrentHashMap<>();
    
    public void setFrames(String userMessageId, List<String> frames) {
        messageFrames.put(userMessageId, frames);
    }
    
    public List<String> getFramesForMessageId(String messageId) {
        return messageFrames.getOrDefault(messageId, List.of());
    }
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
        String userMessageId = request.getMessages().getLast().getId();
        List<String> frames = mockFrameHolder.getFramesForMessageId(userMessageId);
        return Flux.fromIterable(frames).map(chunk -> chunk + "\n");
    }
}

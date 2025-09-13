package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("test")
class MockChatResponseHolder {

    private final ConcurrentHashMap<String, List<Object>> streamParts = new ConcurrentHashMap<>();

    public void setStreamParts(String requestMessageId, List<Object> parts) {
        streamParts.put(requestMessageId, parts);
    }

    public List<Object> getStreamPartsForMessageId(String messageId) {
        return streamParts.getOrDefault(messageId, List.of());
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
    private MockChatResponseHolder mockResponseHolder;

    @Override
    public Flux<String> streamChat(ChatRequest request, StreamPartProcessor processor) {
        String requestMessageId = request.getMessages().getLast().getId();
        List<Object> streamParts = mockResponseHolder.getStreamPartsForMessageId(requestMessageId);

        // Process synchronously in the calling thread for tests
        List<String> jsonParts = new ArrayList<>();
        for (Object streamPart : streamParts) {
            String json = StreamPartProcessorUtils.streamPartToJson(streamPart);
            StreamPartProcessorUtils.processStreamChunk(json, processor);
            jsonParts.add(json);
        }

        return Flux.fromIterable(jsonParts);
    }
}

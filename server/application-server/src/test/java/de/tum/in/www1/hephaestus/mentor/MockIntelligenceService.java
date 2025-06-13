package de.tum.in.www1.hephaestus.mentor;

import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;

public class MockIntelligenceService {

    private final MockWebServer server;

    private MockIntelligenceService() {
        this.server = new MockWebServer();
    }

    public static MockIntelligenceService create() {
        return new MockIntelligenceService();
    }

    public void dispose() throws IOException {
        server.shutdown();
    }

    /**
     * @return the base URL for the mock server
     */
    public HttpUrl getBaseUrl() {
        return server.url("/");
    }

    /**
     * Configure a streaming response for the Vercel AI SDK protocol using the precise
     * chunked transfer encoding approach.
     *
     * @param frames a list of frame strings to stream (each will be its own HTTP chunk)
     * @return this instance for method chaining
     */
    public MockIntelligenceService responseWith(List<String> frames) {
        server.enqueue(ChunkedChatResponseMock.response(frames));
        return this;
    }
}

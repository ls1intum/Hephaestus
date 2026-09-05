package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import com.github.dockerjava.transport.DockerHttpClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.input.CloseShieldInputStream;

/** Keeps response ownership of the transport so rejecting an archive aborts rather than drains it. */
final class ResponseOwnedDockerHttpClient implements DockerHttpClient {

    private final DockerHttpClient delegate;

    ResponseOwnedDockerHttpClient(DockerHttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response execute(Request request) {
        return new OwnedResponse(delegate.execute(request));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private record OwnedResponse(Response delegate) implements Response {
        @Override
        public int getStatusCode() {
            return delegate.getStatusCode();
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            // docker-java closes the body before the response. Apache aborts in Response.close();
            // allowing body.close() first can drain an untrusted chunked response past our byte cap.
            return CloseShieldInputStream.wrap(delegate.getBody());
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}

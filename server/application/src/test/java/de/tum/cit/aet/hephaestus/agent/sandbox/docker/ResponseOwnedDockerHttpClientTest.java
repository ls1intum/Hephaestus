package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ResponseOwnedDockerHttpClientTest extends BaseUnitTest {

    @Mock
    private DockerHttpClient transport;

    @Mock
    private DockerHttpClient.Response response;

    @Test
    void shouldAbortWithoutDrainingWhenCommandStreamClosesEarly() throws IOException {
        var body = new DrainingBody();
        when(transport.execute(any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(body);
        doAnswer(invocation -> {
                    body.aborted = true;
                    body.close();
                    return null;
                })
                .when(response)
                .close();

        try (var client = client()) {
            try (var input = client.copyArchiveFromContainerCmd("container", "/workspace/out")
                    .exec()) {
                assertThat(input.read()).isEqualTo(1);
            }
            assertThat(body.available()).isEqualTo(3);
            assertThat(body.closed).isTrue();
            verify(response).close();
        }
        verify(transport).close();
    }

    @Test
    void shouldPreserveDockerErrorHandlingWhenDaemonRejectsRequest() throws IOException {
        when(transport.execute(any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(404);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
        try (var client = client()) {
            assertThatThrownBy(() -> client.copyArchiveFromContainerCmd("missing", "/workspace/out")
                            .exec())
                    .isInstanceOf(NotFoundException.class);
            verify(response).close();
        }
    }

    private DockerClient client() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .withDockerTlsVerify(false)
                .withApiVersion("1.44")
                .build();
        return DockerClientImpl.getInstance(config, new ResponseOwnedDockerHttpClient(transport));
    }

    /** Models HttpClient's chunked-body close: drain unless the owning response aborted first. */
    private static final class DrainingBody extends ByteArrayInputStream {
        private boolean aborted;
        private boolean closed;

        private DrainingBody() {
            super(new byte[] {1, 2, 3, 4});
        }

        @Override
        public void close() throws IOException {
            if (!aborted) skipNBytes(available());
            closed = true;
            super.close();
        }
    }
}

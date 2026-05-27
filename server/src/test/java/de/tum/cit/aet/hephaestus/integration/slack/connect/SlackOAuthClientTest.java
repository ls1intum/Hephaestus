package de.tum.cit.aet.hephaestus.integration.slack.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.slack.connect.SlackOAuthClient.OAuthV2Access;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlackOAuthClientTest extends BaseUnitTest {

    private MockWebServer slackMock;
    private SlackOAuthClient client;

    @BeforeEach
    void setUp() throws IOException {
        slackMock = new MockWebServer();
        slackMock.start();
        String base = slackMock.url("/").toString().replaceAll("/$", "");
        client = new SlackOAuthClient("test-client-id", "test-client-secret", base);
    }

    @AfterEach
    void tearDown() throws IOException {
        slackMock.close();
    }

    @Test
    void exchangeCode_happyPath_returnsBoundAccessRecord() throws Exception {
        slackMock.enqueue(
            new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    "{\"ok\":true,\"access_token\":\"xoxb-abc\",\"app_id\":\"A1\",\"bot_user_id\":\"U1\"," +
                        "\"team\":{\"id\":\"T1\",\"name\":\"Acme\"}," +
                        "\"scope\":\"chat:write,team:read\"}"
                )
                .build()
        );

        OAuthV2Access r = client.exchangeCode("code-123", "https://example.test/cb");

        assertThat(r.ok()).isTrue();
        assertThat(r.accessToken()).isEqualTo("xoxb-abc");
        assertThat(r.team()).isNotNull();
        assertThat(r.team().id()).isEqualTo("T1");
        assertThat(r.team().name()).isEqualTo("Acme");
        assertThat(r.botUserId()).isEqualTo("U1");
        assertThat(r.appId()).isEqualTo("A1");
        assertThat(r.expiresIn()).isNull();
        assertThat(r.refreshToken()).isNull();

        RecordedRequest req = slackMock.takeRequest();
        assertThat(req.getUrl().encodedPath()).isEqualTo("/api/oauth.v2.access");
        assertThat(req.getHeaders().get("Content-Type")).startsWith("application/x-www-form-urlencoded");
        String body = req.getBody().utf8();
        assertThat(body).contains("client_id=test-client-id");
        assertThat(body).contains("client_secret=test-client-secret");
        assertThat(body).contains("code=code-123");
        assertThat(body).contains("redirect_uri=https%3A%2F%2Fexample.test%2Fcb");
    }

    @Test
    void exchangeCode_slackOkFalse_throwsWithSlackErrorCode() {
        slackMock.enqueue(
            new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("{\"ok\":false,\"error\":\"invalid_code\"}")
                .build()
        );

        assertThatThrownBy(() -> client.exchangeCode("bad", null))
            .isInstanceOf(SlackOAuthException.class)
            .hasMessageContaining("invalid_code");
    }

    @Test
    void exchangeCode_http500_throwsTransportFailure() {
        slackMock.enqueue(new MockResponse.Builder().code(500).body("upstream boom").build());

        assertThatThrownBy(() -> client.exchangeCode("code", null))
            .isInstanceOf(SlackOAuthException.class)
            .hasMessageContaining("transport_failure");
    }

    @Test
    void exchangeCode_malformedJson_throwsTransportFailure() {
        slackMock.enqueue(
            new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("{not json").build()
        );

        assertThatThrownBy(() -> client.exchangeCode("code", null))
            .isInstanceOf(SlackOAuthException.class)
            .hasMessageContaining("transport_failure");
    }

    @Test
    void exchangeCode_returnsRotationFields_passesThroughForStrategyToReject() {
        slackMock.enqueue(
            new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    "{\"ok\":true,\"access_token\":\"xoxe-abc\",\"team\":{\"id\":\"T1\",\"name\":\"Acme\"}," +
                        "\"expires_in\":43200,\"refresh_token\":\"xoxe-1-refresh\"}"
                )
                .build()
        );

        OAuthV2Access r = client.exchangeCode("code", null);

        // The client surfaces rotation fields. The strategy decides whether to reject —
        // see SlackConnectionStrategyTest.finalize_rejectsRotationFields.
        assertThat(r.ok()).isTrue();
        assertThat(r.expiresIn()).isEqualTo(43200);
        assertThat(r.refreshToken()).isEqualTo("xoxe-1-refresh");
    }

    @Test
    void exchangeCode_blankClientCredentials_throwsBeforeHttp() throws IOException {
        SlackOAuthClient unconfigured = new SlackOAuthClient(
            "",
            "",
            slackMock.url("/").toString().replaceAll("/$", "")
        );
        assertThatThrownBy(() -> unconfigured.exchangeCode("code", null))
            .isInstanceOf(SlackOAuthException.class)
            .hasMessageContaining("slack oauth client not configured");
        // No HTTP exchange happened.
        assertThat(slackMock.getRequestCount()).isEqualTo(0);
    }
}

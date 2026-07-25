package de.tum.cit.aet.hephaestus.agent.usage.fx;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.fx.EcbFxRateClient.EcbDailyRate;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parses a verbatim copy of the ECB's {@code eurofxref-daily.xml} and pins the client's central
 * promise: <b>it never throws</b>. Every failure mode — transport, status, body, structure — has to
 * come back as an empty Optional, because the caller is a scheduled tick that must survive the ECB
 * having a bad day.
 */
class EcbFxRateClientTest extends BaseUnitTest {

    private MockWebServer ecb;

    @BeforeEach
    void setUp() throws IOException {
        ecb = new MockWebServer();
        ecb.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        ecb.close();
    }

    private EcbFxRateClient clientFor(String path) {
        return new EcbFxRateClient(ecb.url(path).toString());
    }

    private static String fixture() throws IOException {
        try (InputStream in = EcbFxRateClientTest.class.getResourceAsStream("/agent/fx/eurofxref-daily.xml")) {
            assertThat(in).as("ECB fixture must be on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void enqueueXml(String body) {
        ecb.enqueue(
            new MockResponse.Builder().code(200).addHeader("Content-Type", "text/xml;charset=UTF-8").body(body).build()
        );
    }

    // PARSING

    @Test
    @DisplayName("should read the dated USD rate out of a real ECB document")
    void shouldParseUsdRateWhenGivenRealEcbDocument() throws IOException {
        Optional<EcbDailyRate> parsed = EcbFxRateClient.parseUsdRate(fixture());

        assertThat(parsed).hasValueSatisfying(rate -> {
            assertThat(rate.date()).isEqualTo(LocalDate.of(2026, 7, 24));
            // Stored in the ECB's own direction: US dollars per ONE euro, so above 1.
            assertThat(rate.usdPerEur()).isEqualByComparingTo("1.1377");
        });
    }

    @Test
    @DisplayName("should ignore the other 30 currencies in the document")
    void shouldPickUsdWhenDocumentListsManyCurrencies() throws IOException {
        Optional<EcbDailyRate> parsed = EcbFxRateClient.parseUsdRate(fixture());

        // GBP (0.86545) sits before several others and would be a plausible wrong pick.
        assertThat(parsed.orElseThrow().usdPerEur()).isNotEqualByComparingTo("0.86545");
    }

    @Test
    @DisplayName("should return empty when the document has no USD entry")
    void shouldReturnEmptyWhenNoUsdEntryPresent() {
        String xml = """
            <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
                             xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
              <Cube><Cube time='2026-07-24'><Cube currency='GBP' rate='0.86545'/></Cube></Cube>
            </gesmes:Envelope>
            """;

        assertThat(EcbFxRateClient.parseUsdRate(xml)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when the body is not well-formed XML")
    void shouldReturnEmptyWhenBodyIsNotXml() {
        assertThat(EcbFxRateClient.parseUsdRate("<html><body>503 Service Unavailable")).isEmpty();
    }

    @Test
    @DisplayName("should return empty rather than resolve an external entity")
    void shouldReturnEmptyWhenDocumentDeclaresDoctype() {
        // Remote XML: a DOCTYPE is refused outright rather than parsed, so no entity can be expanded.
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <Cube><Cube time='2026-07-24'><Cube currency='USD' rate='1.1377'/></Cube></Cube>
            """;

        assertThat(EcbFxRateClient.parseUsdRate(xxe)).isEmpty();
    }

    @Test
    @DisplayName("should return empty when the quoted rate is zero")
    void shouldReturnEmptyWhenRateIsZero() {
        // A zero quote would make the read-time inversion divide by zero.
        String xml = "<Cube><Cube time='2026-07-24'><Cube currency='USD' rate='0'/></Cube></Cube>";

        assertThat(EcbFxRateClient.parseUsdRate(xml)).isEmpty();
    }

    // FETCHING

    @Test
    @DisplayName("should fetch and parse the daily document over HTTP")
    void shouldReturnRateWhenEcbAnswers() throws Exception {
        enqueueXml(fixture());

        Optional<EcbDailyRate> fetched = clientFor("/stats/eurofxref/eurofxref-daily.xml").fetchLatestUsdRate();

        assertThat(fetched).hasValueSatisfying(rate -> assertThat(rate.usdPerEur()).isEqualByComparingTo("1.1377"));
        assertThat(ecb.takeRequest().getTarget()).isEqualTo("/stats/eurofxref/eurofxref-daily.xml");
    }

    @Test
    @DisplayName("should return empty without throwing when the ECB answers 500")
    void shouldReturnEmptyWhenEcbReturnsServerError() {
        ecb.enqueue(new MockResponse.Builder().code(500).body("upstream boom").build());

        assertThat(clientFor("/daily.xml").fetchLatestUsdRate()).isEmpty();
    }

    @Test
    @DisplayName("should return empty without throwing when the body is empty")
    void shouldReturnEmptyWhenEcbReturnsEmptyBody() {
        ecb.enqueue(new MockResponse.Builder().code(200).body("").build());

        assertThat(clientFor("/daily.xml").fetchLatestUsdRate()).isEmpty();
    }

    @Test
    @DisplayName("should return empty without throwing when the host is unreachable")
    void shouldReturnEmptyWhenHostIsUnreachable() throws IOException {
        String deadUrl = ecb.url("/daily.xml").toString();
        ecb.close();

        assertThat(new EcbFxRateClient(deadUrl).fetchLatestUsdRate()).isEmpty();
    }
}

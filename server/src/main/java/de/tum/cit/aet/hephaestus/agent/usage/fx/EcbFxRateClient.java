package de.tum.cit.aet.hephaestus.agent.usage.fx;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the European Central Bank's daily euro foreign-exchange reference rates — a free, key-less,
 * ~10 KB XML document published on TARGET working days at around 16:00 CET.
 *
 * <p>The document nests one dated {@code <Cube time='YYYY-MM-DD'>} holding one
 * {@code <Cube currency='…' rate='…'/>} per currency. Rates are quoted as foreign units per ONE
 * EURO, so the USD entry reads {@code 1.1377} = "1 EUR buys 1.1377 USD". That direction is stored
 * verbatim; the inversion clients need happens once, on read, in {@link FxRateInfoDTO#fromEcbRate}.
 *
 * <p><b>Contract: this client never throws.</b> A timeout, a 5xx, an unparseable body or a missing
 * USD entry all come back as {@link Optional#empty()} with a warning. A display-only nicety must not
 * be able to fail a scheduled tick, and a fetch that returns nothing simply leaves the last stored
 * rate in place.
 */
@Component
@WorkspaceAgnostic("Public reference rates are instance-wide reference data, not tenant data")
public class EcbFxRateClient {

    /** The canonical ECB daily file. */
    public static final String ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

    private static final Logger log = LoggerFactory.getLogger(EcbFxRateClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CUBE = "Cube";

    private final RestClient restClient;
    private final String dailyUrl;

    /**
     * @param dailyUrl overridable only so an air-gapped instance can point at a local mirror (and so
     *     tests can serve a fixture). Unset — the normal case — it is the ECB's own URL.
     */
    public EcbFxRateClient(@Value("${hephaestus.llm.fx.daily-url:" + ECB_DAILY_URL + "}") String dailyUrl) {
        this.dailyUrl = dailyUrl;
        // Deliberately its own short-timeout client: a slow ECB must never hold a scheduler thread,
        // and this shares no configuration with the LLM proxy or any job-path client.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** The most recent published USD rate, or empty if the fetch or the parse did not succeed. */
    public Optional<EcbDailyRate> fetchLatestUsdRate() {
        String body;
        try {
            body = restClient.get().uri(dailyUrl).retrieve().body(String.class);
        } catch (Exception e) {
            // Includes RestClientException and any I/O failure underneath it. Warn, keep the stored
            // rate, try again on the next tick.
            log.warn(
                "fx: ECB rate fetch failed, keeping the last stored rate: url={} reason={}",
                dailyUrl,
                e.toString()
            );
            return Optional.empty();
        }
        if (body == null || body.isBlank()) {
            log.warn("fx: ECB returned an empty body: url={}", dailyUrl);
            return Optional.empty();
        }
        return parseUsdRate(body);
    }

    /**
     * Extract the dated USD rate from an ECB {@code eurofxref-daily.xml} document.
     *
     * <p>Package-private and namespace-agnostic on purpose: the ECB document puts the {@code Cube}
     * elements in a default namespace, so matching by local name keeps the parse working if the ECB
     * ever renames or drops the prefix. External entities and DTDs are disabled — this is remote XML.
     */
    static Optional<EcbDailyRate> parseUsdRate(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            var document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList cubes = document.getElementsByTagNameNS("*", CUBE);
            for (int i = 0; i < cubes.getLength(); i++) {
                Element dated = (Element) cubes.item(i);
                String time = dated.getAttribute("time");
                if (time.isBlank()) {
                    continue;
                }
                Optional<BigDecimal> usd = findUsdRate(dated);
                if (usd.isPresent()) {
                    return Optional.of(new EcbDailyRate(LocalDate.parse(time), usd.get()));
                }
                log.warn("fx: ECB document has no USD entry for {}", time);
            }
            log.warn("fx: ECB document contained no dated Cube element");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("fx: could not parse the ECB rate document: reason={}", e.toString());
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> findUsdRate(Element datedCube) {
        NodeList children = datedCube.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !CUBE.equals(child.getLocalName())) {
                continue;
            }
            Element currencyCube = (Element) child;
            if (!"USD".equals(currencyCube.getAttribute("currency"))) {
                continue;
            }
            String rate = currencyCube.getAttribute("rate");
            if (rate.isBlank()) {
                return Optional.empty();
            }
            BigDecimal parsed = new BigDecimal(rate);
            // A zero or negative quote would make the read-time inversion divide by zero or flip sign.
            return parsed.signum() > 0 ? Optional.of(parsed) : Optional.empty();
        }
        return Optional.empty();
    }

    /** One published day: the ECB's own direction, US dollars per 1 EUR. */
    public record EcbDailyRate(LocalDate date, BigDecimal usdPerEur) {}
}

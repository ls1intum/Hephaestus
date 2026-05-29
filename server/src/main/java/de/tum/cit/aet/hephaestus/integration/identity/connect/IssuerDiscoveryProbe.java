package de.tum.cit.aet.hephaestus.integration.identity.connect;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates a workspace-supplied OIDC issuer URL before its {@code client_secret} is
 * persisted. Defends against an SSRF vector: a malicious workspace admin could otherwise
 * register an "issuer" pointing at an internal address (cloud metadata endpoint, a sibling
 * service, localhost) and use the registration / test-login dance to make the server issue
 * requests there.
 *
 * <h2>Checks, in order</h2>
 * <ol>
 *   <li>Scheme must be {@code https} (no {@code http}, no {@code file}, no {@code gopher}).</li>
 *   <li>Host must resolve only to public, routable addresses — every resolved IP is
 *       rejected if loopback, link-local, site-local (RFC 1918), unique-local,
 *       multicast, or wildcard.</li>
 *   <li>{@code GET {issuer}/.well-known/openid-configuration} must return 200 with a JSON
 *       body carrying {@code issuer}, {@code authorization_endpoint}, and
 *       {@code token_endpoint}.</li>
 *   <li>The {@code authorization_endpoint} and {@code token_endpoint} hosts are themselves
 *       re-validated for private-IP safety (an attacker could publish a public discovery
 *       doc that points the actual flow at an internal address).</li>
 * </ol>
 *
 * <h2>DNS-rebind / TOCTOU hardening (OWASP SSRF Prevention Cheat Sheet)</h2>
 * Validating the host's IP and then letting {@link HttpClient} re-resolve DNS at send time is a
 * classic TOCTOU bypass: an attacker domain with a very low TTL can answer with a public IP during
 * {@link #assertPublicHost} and then flip to {@code 169.254.169.254} / {@code 127.0.0.1} for the
 * actual connection. To collapse that window we resolve the host ONCE, validate every returned
 * address, and then re-resolve immediately before the send and require the live answer to be a
 * non-empty subset of the already-vetted public set (re-validating each address again). Any new or
 * non-public address aborts the request. Combined with {@link HttpClient.Redirect#NEVER} (a 30x to
 * an internal address is a classic bypass) and a short timeout this leaves no usable rebind window.
 */
@Component
public class IssuerDiscoveryProbe {

    private static final Logger log = LoggerFactory.getLogger(IssuerDiscoveryProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(TIMEOUT)
        .build();

    private final HostResolver resolver;

    public IssuerDiscoveryProbe() {
        this(InetAddress::getAllByName);
    }

    /** Test seam: a stub resolver lets the SSRF matrix run without real DNS. */
    IssuerDiscoveryProbe(HostResolver resolver) {
        this.resolver = resolver;
    }

    /** DNS resolution seam (defaults to {@link InetAddress#getAllByName(String)}). */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /** Thrown when the issuer fails any safety or discovery check. Message is user-safe. */
    public static class IssuerValidationException extends RuntimeException {

        public IssuerValidationException(String message) {
            super(message);
        }
    }

    /** Validates the issuer; returns the parsed discovery document on success. */
    public DiscoveryResult validate(String issuerUrl) {
        URI issuer = parseHttpsUri(issuerUrl, "issuer URL");
        // First-pass validation; the actual fetch re-resolves and re-checks (DNS-rebind defence).
        assertPublicHost(issuer);

        URI discovery = issuer.resolve(stripTrailingSlash(issuer.getPath()) + "/.well-known/openid-configuration");
        JsonNode doc = fetchDiscovery(discovery);

        String docIssuer = requireText(doc, "issuer");
        URI authEndpoint = parseHttpsUri(requireText(doc, "authorization_endpoint"), "authorization_endpoint");
        URI tokenEndpoint = parseHttpsUri(requireText(doc, "token_endpoint"), "token_endpoint");
        assertPublicHost(authEndpoint);
        assertPublicHost(tokenEndpoint);

        return new DiscoveryResult(docIssuer, authEndpoint.toString(), tokenEndpoint.toString());
    }

    private JsonNode fetchDiscovery(URI discovery) {
        // Re-resolve + re-validate immediately before the send so a low-TTL rebind between the
        // initial assertPublicHost() and now cannot point the connection at an internal address.
        // The live answer must be a non-empty subset of the vetted public set.
        Set<String> vetted = assertPublicHost(discovery);
        Set<String> live = resolveAndValidate(discovery.getHost());
        if (!vetted.containsAll(live)) {
            log.warn(
                "auth.oidc: rejecting discovery — host {} re-resolved to a new/changed address (possible DNS rebind)",
                discovery.getHost()
            );
            throw new IssuerValidationException(
                "host " + discovery.getHost() + " resolved to a different address on re-check (possible DNS rebind)"
            );
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(discovery)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IssuerValidationException(
                    "OIDC discovery at " + discovery + " returned HTTP " + resp.statusCode()
                );
            }
            return MAPPER.readTree(resp.body());
        } catch (IssuerValidationException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            throw new IssuerValidationException("OIDC discovery at " + discovery + " timed out");
        } catch (Exception e) {
            throw new IssuerValidationException("OIDC discovery at " + discovery + " failed: " + e.getMessage());
        }
    }

    private static URI parseHttpsUri(String value, String field) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IssuerValidationException(field + " is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IssuerValidationException(field + " must use https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IssuerValidationException(field + " has no host");
        }
        return uri;
    }

    /**
     * Resolve the host and reject if any returned IP is non-public. Returns the set of vetted
     * address strings (so the caller can pin against a later re-resolution).
     */
    private Set<String> assertPublicHost(URI uri) {
        return resolveAndValidate(uri.getHost());
    }

    private Set<String> resolveAndValidate(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IssuerValidationException("host " + host + " does not resolve");
        }
        if (addresses == null || addresses.length == 0) {
            throw new IssuerValidationException("host " + host + " resolved to no addresses");
        }
        for (InetAddress addr : addresses) {
            if (
                addr.isLoopbackAddress() ||
                addr.isLinkLocalAddress() ||
                addr.isSiteLocalAddress() ||
                addr.isAnyLocalAddress() ||
                addr.isMulticastAddress() ||
                isUniqueLocalIpv6(addr)
            ) {
                throw new IssuerValidationException(
                    "host " + host + " resolves to a non-public address (" + addr.getHostAddress() + ")"
                );
            }
        }
        Set<String> out = new HashSet<>();
        for (InetAddress addr : addresses) {
            out.add(addr.getHostAddress());
        }
        return out;
    }

    /** fc00::/7 — IPv6 unique-local; not covered by isSiteLocalAddress(). */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private static String requireText(JsonNode doc, String field) {
        JsonNode node = doc.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IssuerValidationException("OIDC discovery document is missing '" + field + "'");
        }
        return node.asText();
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /** Parsed + validated discovery output. */
    public record DiscoveryResult(String issuer, String authorizationEndpoint, String tokenEndpoint) {}
}

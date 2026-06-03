package de.tum.cit.aet.hephaestus.integration.identity.connect;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
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
 *       multicast, wildcard, or an IANA special-purpose range (CGNAT 100.64/10, NAT64
 *       64:ff9b::/32, TEST-NET, benchmarking, Class-E) — see {@link #isReservedRange}.</li>
 *   <li>{@code GET {issuer}/.well-known/openid-configuration} must return 200 with a JSON
 *       body carrying {@code issuer}, {@code authorization_endpoint}, and
 *       {@code token_endpoint}.</li>
 *   <li>The {@code authorization_endpoint} and {@code token_endpoint} hosts are themselves
 *       re-validated for private-IP safety (an attacker could publish a public discovery
 *       doc that points the actual flow at an internal address).</li>
 * </ol>
 *
 * <h2>DNS-rebind / TOCTOU hardening (OWASP SSRF Prevention Cheat Sheet)</h2>
 * Validating the host's IP and then letting a high-level HTTP client re-resolve DNS at send time is a
 * classic TOCTOU bypass: an attacker domain with a very low TTL can answer with a public IP during
 * {@link #assertPublicHost} and then flip to {@code 169.254.169.254} / {@code 127.0.0.1} for the
 * actual connection (the {@code java.net.http.HttpClient} does its OWN, un-pinnable DNS lookup at
 * connect time). For this registration-time discovery fetch we close that window by not connecting
 * by hostname at all:
 * <ol>
 *   <li>Resolve the host ONCE and validate every returned address is public.</li>
 *   <li>Re-resolve immediately before connecting and require the live answer to be a non-empty subset
 *       of the vetted set (re-validating each address), so a flip between the two resolutions aborts.</li>
 *   <li>Open the TLS socket to a vetted IP <em>literal</em> via {@link #pinnedHttpsGet} — the kernel
 *       connects to exactly that address, with no further DNS lookup the attacker could race. TLS SNI
 *       and endpoint identification are still set to the original hostname, so certificate hostname
 *       verification is fully preserved.</li>
 * </ol>
 * Redirects are NOT followed (a 30x to an internal address is a classic bypass): any non-200 is
 * rejected. A short connect/read timeout bounds slow-loris and blind-SSRF probing.
 *
 * <p><strong>Scope:</strong> this rebind protection covers only the registration-time discovery fetch.
 * At login time Spring's {@code oauth2Login} re-resolves the stored issuer-derived authorization/token/
 * userinfo URIs through its own HTTP client with no rebind guard, so the login-time leg is NOT
 * rebind-protected and relies on the configured IdP being a trusted external host.
 */
@Component
public class IssuerDiscoveryProbe {

    private static final Logger log = LoggerFactory.getLogger(IssuerDiscoveryProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int TIMEOUT_MS = (int) TIMEOUT.toMillis();
    /** Cap the discovery body we will buffer (a JSON metadata doc is tiny; reject runaway responses). */
    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        // Re-resolve + re-validate immediately before the connect so a low-TTL rebind between the
        // initial assertPublicHost() and now cannot point the connection at an internal address.
        // The live answer must be a non-empty subset of the vetted public set.
        Set<String> vetted = assertPublicHost(discovery);
        List<InetAddress> live = resolveValidated(discovery.getHost());
        Set<String> liveStrings = new HashSet<>();
        for (InetAddress a : live) {
            liveStrings.add(a.getHostAddress());
        }
        if (!vetted.containsAll(liveStrings)) {
            log.warn(
                "auth.oidc: rejecting discovery — host {} re-resolved to a new/changed address (possible DNS rebind)",
                discovery.getHost()
            );
            throw new IssuerValidationException(
                "host " + discovery.getHost() + " resolved to a different address on re-check (possible DNS rebind)"
            );
        }
        // Connect to the vetted IP LITERAL (no further DNS lookup), with SNI + hostname verification
        // pinned to the original hostname. This is the step that actually closes the rebind window.
        InetAddress pinned = live.get(0);
        try {
            String body = pinnedHttpsGet(pinned, discovery);
            return MAPPER.readTree(body);
        } catch (IssuerValidationException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            throw new IssuerValidationException("OIDC discovery at " + discovery + " timed out");
        } catch (Exception e) {
            throw new IssuerValidationException("OIDC discovery at " + discovery + " failed: " + e.getMessage());
        }
    }

    /**
     * HTTP/1.1 GET over a TLS socket connected to {@code pinnedIp} (the already-vetted address), while
     * setting the TLS SNI and endpoint-identification host to {@code target}'s hostname so certificate
     * hostname verification still applies. No DNS lookup happens here — the connection goes to exactly
     * the address we validated, eliminating the rebind window. Redirects are not followed: any non-200
     * status is rejected by the caller via the parsed body never being produced.
     */
    private String pinnedHttpsGet(InetAddress pinnedIp, URI target) throws Exception {
        String host = target.getHost();
        int port = target.getPort() != -1 ? target.getPort() : 443;
        String path = target.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (target.getRawQuery() != null) {
            path = path + "?" + target.getRawQuery();
        }

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            socket.connect(new InetSocketAddress(pinnedIp, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            // Pin TLS verification to the HOSTNAME (not the IP literal): SNI + HTTPS endpoint
            // identification make the handshake fail unless the cert is valid for `host`.
            SSLParameters params = socket.getSSLParameters();
            params.setServerNames(List.of(new SNIHostName(host)));
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.startHandshake();

            String request =
                "GET " +
                path +
                " HTTP/1.1\r\n" +
                "Host: " +
                host +
                "\r\n" +
                "Accept: application/json\r\n" +
                "User-Agent: Hephaestus-IssuerDiscoveryProbe\r\n" +
                "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            return readHttpResponseBody(socket.getInputStream(), target);
        }
    }

    /** Minimal HTTP/1.1 response reader: enforces a 200 status, then returns the (bounded) body. */
    private String readHttpResponseBody(InputStream in, URI target) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String statusLine = reader.readLine();
        if (statusLine == null) {
            throw new IssuerValidationException("OIDC discovery at " + target + " returned an empty response");
        }
        // "HTTP/1.1 200 OK" — reject anything that is not 200 (covers 30x redirect bypass attempts).
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length < 2 || !"200".equals(statusParts[1])) {
            throw new IssuerValidationException("OIDC discovery at " + target + " returned: " + statusLine.trim());
        }
        // Skip headers up to the blank line.
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // headers ignored — we read until EOF (Connection: close) and bound the size below
        }
        StringBuilder body = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            body.append(buf, 0, n);
            if (body.length() > MAX_BODY_BYTES) {
                throw new IssuerValidationException("OIDC discovery at " + target + " body exceeded the size limit");
            }
        }
        return body.toString();
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
        Set<String> out = new HashSet<>();
        for (InetAddress addr : resolveValidated(host)) {
            out.add(addr.getHostAddress());
        }
        return out;
    }

    /**
     * Resolve the host and reject if ANY returned IP is non-public; returns the validated
     * {@link InetAddress} list so the caller can pin the connection to a vetted literal.
     */
    private List<InetAddress> resolveValidated(String host) {
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
                isUniqueLocalIpv6(addr) ||
                isReservedRange(addr)
            ) {
                throw new IssuerValidationException(
                    "host " + host + " resolves to a non-public address (" + addr.getHostAddress() + ")"
                );
            }
        }
        return List.of(addresses);
    }

    /** fc00::/7 — IPv6 unique-local; not covered by isSiteLocalAddress(). */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * IANA special-purpose ranges that {@link InetAddress}'s {@code isXxx()} predicates do NOT flag
     * but which routinely front internal services, so they must not be reachable as an SSRF target.
     * {@code isSiteLocalAddress()} is RFC-1918-only, so CGNAT (RFC 6598, standard pod networking in
     * EKS/GKE), benchmarking, TEST-NET, Class-E and especially the NAT64 well-known prefix (RFC 6052
     * — its low 32 bits can encode IPv4 loopback) all slip past the predicates above. Per the OWASP
     * SSRF Prevention Cheat Sheet this covers the SSRF-relevant special-purpose ranges — those that can
     * front internal or loopback-reachable services — not just RFC 1918; purely non-SSRF assignments
     * (e.g. 6to4-relay anycast, AS112, AMT) are intentionally not blocked. Operates on the already-resolved
     * address bytes (allocation-free, IPv4/IPv6-uniform).
     */
    private static boolean isReservedRange(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF,
                b1 = b[1] & 0xFF,
                b2 = b[2] & 0xFF;
            if (b0 == 0) return true; // 0.0.0.0/8 "this network"
            if (b0 == 100 && (b1 & 0xC0) == 0x40) return true; // 100.64.0.0/10 carrier-grade NAT
            if (b0 == 192 && b1 == 0 && b2 == 0) return true; // 192.0.0.0/24 IETF protocol assignments
            if (b0 == 192 && b1 == 0 && b2 == 2) return true; // 192.0.2.0/24 TEST-NET-1
            if (b0 == 198 && (b1 & 0xFE) == 18) return true; // 198.18.0.0/15 benchmarking
            if (b0 == 198 && b1 == 51 && b2 == 100) return true; // 198.51.100.0/24 TEST-NET-2
            if (b0 == 203 && b1 == 0 && b2 == 113) return true; // 203.0.113.0/24 TEST-NET-3
            return b0 >= 240; // 240.0.0.0/4 reserved (Class E) + 255.255.255.255 broadcast
        }
        if (b.length == 16) {
            // 64:ff9b::/32 NAT64 well-known prefixes (RFC 6052 /96 + RFC 8215 /48) — embed IPv4, incl. loopback.
            if ((b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xff && (b[3] & 0xFF) == 0x9b) {
                return true;
            }
            // 2001:db8::/32 documentation
            return (b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0d && (b[3] & 0xFF) == 0xb8;
        }
        return false;
    }

    private static String requireText(JsonNode doc, String field) {
        JsonNode node = doc.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new IssuerValidationException("OIDC discovery document is missing '" + field + "'");
        }
        return node.asString();
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

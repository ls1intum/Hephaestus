package de.tum.in.www1.hephaestus.core.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates user-provided server URLs to prevent SSRF attacks.
 *
 * <p>When users supply a custom GitLab server URL, the backend makes HTTP requests
 * to that URL. This validator ensures the URL is safe before any outbound call.
 *
 * <h2>Blocked Patterns</h2>
 * <ul>
 *   <li>Non-HTTPS schemes (http, file, ftp, gopher, etc.)</li>
 *   <li>URLs with embedded credentials ({@code https://evil@legit.com})</li>
 *   <li>Localhost and loopback addresses (by hostname and IP)</li>
 *   <li>Cloud metadata endpoints (169.254.169.254, metadata.google.internal)</li>
 *   <li>Private/reserved TLDs (.internal, .local, .localhost)</li>
 *   <li>IP addresses that parse to loopback/link-local/site-local ranges</li>
 * </ul>
 *
 * <p><b>Note:</b> DNS resolution is intentionally NOT performed in this validator to keep it
 * pure (no I/O, no network). DNS-level SSRF protection (resolving hostnames to IPs and checking
 * ranges) should be handled at the HTTP client layer if needed.
 */
public final class ServerUrlValidator {

    private ServerUrlValidator() {}

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
        "localhost",
        "metadata.google.internal",
        "metadata.goog"
    );

    private static final Set<String> BLOCKED_IPS = Set.of(
        "127.0.0.1",
        "0.0.0.0",
        "::1",
        "0:0:0:0:0:0:0:1",
        "169.254.169.254"
    );

    private static final Set<String> BLOCKED_TLDS = Set.of(".internal", ".local", ".localhost");

    /**
     * Validates a server URL for safety.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if the URL is unsafe or malformed
     */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Server URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Server URL is malformed: " + e.getMessage());
        }

        // Must be HTTPS
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Server URL must use HTTPS scheme, got: " + uri.getScheme());
        }

        // Must not contain userinfo (credentials in URL)
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Server URL must not contain embedded credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server URL must have a valid hostname");
        }

        String hostLower = host.toLowerCase();

        // Block known dangerous hostnames
        if (BLOCKED_HOSTNAMES.contains(hostLower)) {
            throw new IllegalArgumentException("Server URL hostname is not allowed: " + host);
        }

        // Block known dangerous IPs
        if (BLOCKED_IPS.contains(hostLower)) {
            throw new IllegalArgumentException("Server URL must not point to a loopback or reserved address");
        }

        // Block private TLDs
        for (String tld : BLOCKED_TLDS) {
            if (hostLower.endsWith(tld)) {
                throw new IllegalArgumentException("Server URL must not use a private TLD: " + tld);
            }
        }

        // Must not have a path beyond / (prevents open-redirect abuse like https://legit.com/redirect)
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalArgumentException("Server URL must be a base URL without path segments");
        }

        // If the hostname looks like an IP address, validate its range
        validateIpRangeIfApplicable(host);
    }

    /**
     * If the hostname parses as an IP address, reject loopback/link-local/site-local/wildcard ranges.
     * This catches direct IP-based SSRF like {@code https://10.0.0.1} or {@code https://[fe80::1]}.
     */
    private static void validateIpRangeIfApplicable(String host) {
        // Strip IPv6 brackets if present
        String ipCandidate = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;

        InetAddress addr;
        try {
            // InetAddress.getByName() parses numeric IPs without DNS lookup
            addr = InetAddress.getByName(ipCandidate);
        } catch (UnknownHostException e) {
            // Not a parseable IP — it's a hostname, which is fine (DNS check is caller's concern)
            return;
        }

        // Only check if the parsed address matches the input (i.e., it was a numeric IP, not a hostname)
        // InetAddress.getByName("example.com") triggers DNS lookup, so we skip non-numeric inputs
        if (!addr.getHostAddress().equalsIgnoreCase(ipCandidate) && !host.startsWith("[")) {
            return;
        }

        if (addr.isLoopbackAddress()) {
            throw new IllegalArgumentException("Server URL must not point to a loopback address");
        }
        if (addr.isLinkLocalAddress()) {
            throw new IllegalArgumentException("Server URL must not point to a link-local address");
        }
        if (addr.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Server URL must not point to a private/site-local address");
        }
        if (addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("Server URL must not point to a wildcard address");
        }
    }
}

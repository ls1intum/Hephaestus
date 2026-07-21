package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF egress guard for instance-owned LLM provider connections (#1368). Shared: the LLM proxy will
 * reuse it to vet where credentialed traffic may egress. Kept a plain component (its only dependencies
 * are the {@link InstanceLlmSettingsRepository} singleton and the loopback dev toggle) so it is
 * straightforward to unit-test.
 *
 * <p>A base URL passes only when it (1) parses with no userinfo, query string, or fragment — those
 * are how a gateway URL smuggles a credential (e.g. {@code https://gw/v1?api-key=SECRET}) past the
 * guard and into snapshots/DTOs/logs, (2) is HTTPS — or plain HTTP but strictly to a localhost/loopback
 * dev target AND {@link #allowLoopback} is enabled (production defaults it OFF: an unconditional
 * loopback allowance is an SSRF hole letting a workspace admin point their "provider" at host-local
 * services), (3) does not resolve to a private / link-local / loopback / unique-local address (which
 * blocks the cloud metadata endpoint {@code 169.254.169.254} as a link-local address) — this check is
 * unconditional and independent of {@link #allowLoopback}, and (4) — when the instance allowlist is
 * non-blank — has a host that appears verbatim in that allowlist. An empty allowlist permits any public
 * host.
 */
@Component
@WorkspaceAgnostic("Instance egress policy reads the global instance_llm_settings singleton, not tenant data")
public class EgressPolicy {

    private static final String NOT_PUBLIC_HTTPS = "Provider host must be a public HTTPS URL";
    private static final String NO_CREDENTIALS_OR_QUERY =
        "Provider URLs must not contain credentials or query parameters.";
    private static final Set<String> LOCAL_DEV_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final InstanceLlmSettingsRepository settingsRepository;

    /**
     * Gates the localhost/loopback HTTP allowance (default {@code false} — production SSRF hardening).
     * Mirrors how other dev-only toggles, e.g. {@code hephaestus.auth.dev-login-enabled}, are
     * property-gated: off unless a local/dev profile explicitly turns it on.
     */
    private final boolean allowLoopback;

    public EgressPolicy(
        InstanceLlmSettingsRepository settingsRepository,
        @Value("${hephaestus.llm.egress.allow-loopback:false}") boolean allowLoopback
    ) {
        this.settingsRepository = settingsRepository;
        this.allowLoopback = allowLoopback;
    }

    /**
     * @throws IllegalArgumentException with an operator-facing message when {@code baseUrl} is not a
     *     permitted egress target. Mapped to HTTP 400 by the global {@code ProblemDetail} advice.
     */
    public void validate(String baseUrl) {
        URI uri = parse(baseUrl);
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(NO_CREDENTIALS_OR_QUERY);
        }
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || host.isBlank() || scheme == null) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean localDev = allowLoopback && LOCAL_DEV_HOSTS.contains(normalizedHost);

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        boolean https = normalizedScheme.equals("https");
        boolean httpLocalDev = normalizedScheme.equals("http") && localDev;
        if (!https && !httpLocalDev) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }

        if (!localDev) {
            assertPublicAddress(normalizedHost);
            assertAllowlisted(normalizedHost);
        }
    }

    private static URI parse(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        try {
            return new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
    }

    /** Resolves the host and refuses if any resolved address is private/loopback/link-local/unique-local. */
    private static void assertPublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException(NOT_PUBLIC_HTTPS);
            }
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (
            address.isLoopbackAddress() ||
            address.isLinkLocalAddress() ||
            address.isSiteLocalAddress() ||
            address.isAnyLocalAddress()
        ) {
            return true;
        }
        byte[] octets = address.getAddress();
        if (octets.length == 4) {
            int first = octets[0] & 0xFF;
            int second = octets[1] & 0xFF;
            // 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16 (isSiteLocal/isLinkLocal cover most of
            // these, but re-assert to stay correct if the JDK's classification ever narrows).
            return (
                first == 10 ||
                (first == 172 && second >= 16 && second <= 31) ||
                (first == 192 && second == 168) ||
                first == 127 ||
                (first == 169 && second == 254)
            );
        }
        // IPv6 unique-local fc00::/7 (first 7 bits 1111 110). fe80::/10 link-local and ::1 loopback
        // are already caught above.
        return (octets[0] & 0xFE) == 0xFC;
    }

    private void assertAllowlisted(String host) {
        InstanceLlmSettings settings = settingsRepository.findById((short) 1).orElse(null);
        String allowlist = settings != null ? settings.getAllowedEgressHosts() : null;
        if (allowlist == null || allowlist.isBlank()) {
            return;
        }
        Set<String> allowed = Arrays.stream(allowlist.split("[,\\n\\r]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        if (!allowed.contains(host)) {
            throw new IllegalArgumentException("Provider host " + host + " is not in the allowed list");
        }
    }
}

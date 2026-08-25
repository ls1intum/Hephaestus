package de.tum.cit.aet.hephaestus.integration.identity.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.identity.connect.IssuerDiscoveryProbe.IssuerValidationException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SSRF regression suite for {@link IssuerDiscoveryProbe}. The DNS resolver is stubbed so the matrix
 * runs deterministically without real DNS or HTTP. Private / loopback / link-local / multicast /
 * wildcard / unique-local and DNS-rebind answers must all be rejected at the host-validation stage
 * (before any outbound connection); a public answer must pass validation and proceed to the fetch.
 */
class IssuerDiscoveryProbeTest extends BaseUnitTest {

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Resolver that always returns the same address(es). */
    private static IssuerDiscoveryProbe probeResolvingTo(String... ips) {
        InetAddress[] addrs = new InetAddress[ips.length];
        for (int i = 0; i < ips.length; i++) {
            addrs[i] = ip(ips[i]);
        }
        return new IssuerDiscoveryProbe(host -> addrs);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "127.0.0.1", // loopback
            "10.0.0.5", // RFC 1918 site-local
            "192.168.1.10", // RFC 1918
            "172.16.0.1", // RFC 1918
            "169.254.169.254", // link-local — cloud metadata endpoint
            "0.0.0.0", // wildcard
            "::1", // IPv6 loopback
            "fe80::1", // IPv6 link-local
            "fc00::1", // IPv6 unique-local
            "fd00::1", // IPv6 unique-local
            "224.0.0.1", // multicast
            "100.64.0.1", // CGNAT (RFC 6598) — standard EKS/GKE pod networking
            "100.127.255.255", // CGNAT upper bound
            "192.0.0.1", // IETF protocol assignments (RFC 6890)
            "192.0.2.1", // TEST-NET-1
            "198.18.0.1", // benchmarking (RFC 2544)
            "198.51.100.1", // TEST-NET-2
            "203.0.113.1", // TEST-NET-3
            "240.0.0.1", // Class E reserved
            "255.255.255.255", // limited broadcast
            "64:ff9b::7f00:1", // NAT64 (RFC 6052) of 127.0.0.1 — loopback bypass
            "2001:db8::1", // documentation prefix
        }
    )
    void rejectsNonPublicResolvedAddress(String privateIp) {
        IssuerDiscoveryProbe probe = probeResolvingTo(privateIp);
        assertThatThrownBy(() -> probe.validate("https://issuer.example.test"))
            .isInstanceOf(IssuerValidationException.class)
            .hasMessageContaining("non-public address");
    }

    @Test
    void rejectsWhenAnyResolvedAddressIsPrivate() {
        // One public + one private → reject (an attacker can steer to the private one).
        IssuerDiscoveryProbe probe = probeResolvingTo("93.184.216.34", "10.0.0.1");
        assertThatThrownBy(() -> probe.validate("https://issuer.example.test"))
            .isInstanceOf(IssuerValidationException.class)
            .hasMessageContaining("non-public address");
    }

    @Test
    void rejectsUnresolvableHost() {
        IssuerDiscoveryProbe probe = new IssuerDiscoveryProbe(host -> {
            throw new UnknownHostException(host);
        });
        assertThatThrownBy(() -> probe.validate("https://issuer.example.test"))
            .isInstanceOf(IssuerValidationException.class)
            .hasMessageContaining("does not resolve");
    }

    @Test
    void rejectsEmptyResolution() {
        IssuerDiscoveryProbe probe = new IssuerDiscoveryProbe(host -> new InetAddress[0]);
        assertThatThrownBy(() -> probe.validate("https://issuer.example.test"))
            .isInstanceOf(IssuerValidationException.class)
            .hasMessageContaining("no addresses");
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://issuer.example.test", "ftp://issuer.example.test", "file:///etc/passwd" })
    void rejectsNonHttpsScheme(String url) {
        IssuerDiscoveryProbe probe = probeResolvingTo("93.184.216.34");
        assertThatThrownBy(() -> probe.validate(url))
            .isInstanceOf(IssuerValidationException.class)
            .hasMessageContaining("https");
    }

    @Test
    void rejectsDnsRebindBetweenValidationAndFetch() {
        // First resolution (assertPublicHost in validate) → public; the re-resolution inside
        // fetchDiscovery → loopback. The pin (live ⊄ vetted) must abort before any connection.
        Deque<InetAddress[]> answers = new ArrayDeque<>();
        answers.add(new InetAddress[] { ip("93.184.216.34") }); // validate() first pass
        answers.add(new InetAddress[] { ip("93.184.216.34") }); // fetchDiscovery vetted pass
        answers.add(new InetAddress[] { ip("127.0.0.1") }); // fetchDiscovery live re-resolve → rebind
        IssuerDiscoveryProbe probe = new IssuerDiscoveryProbe(host -> {
            InetAddress[] next = answers.poll();
            return next != null ? next : new InetAddress[] { ip("127.0.0.1") };
        });

        assertThatThrownBy(() -> probe.validate("https://issuer.example.test")).isInstanceOf(
            IssuerValidationException.class
        );
    }

    @Test
    void publicHostPassesValidationAndProceedsToFetch() {
        // A public resolution must NOT be rejected at the host-validation stage. The fetch itself
        // then fails (no server at this IP), but crucially NOT with a "non-public address" SSRF
        // rejection — proving the public host cleared every safety gate.
        IssuerDiscoveryProbe probe = probeResolvingTo("93.184.216.34");
        assertThatThrownBy(() -> probe.validate("https://issuer.invalid.example"))
            .isInstanceOf(IssuerValidationException.class)
            .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("non-public address"));
    }
}

package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The SSRF target predicate that backs both {@code IssuerDiscoveryProbe} and the
 * {@code SsrfGuardedResolverGroup} HTTP-client resolver. All inputs are IP literals so
 * {@link InetAddress#getByName} performs no DNS.
 */
class PrivateAddressGuardTest extends BaseUnitTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "127.0.0.1", // loopback
            "::1", // IPv6 loopback
            "0.0.0.0", // wildcard / "this network"
            "10.0.0.5", // RFC 1918
            "172.16.0.1", // RFC 1918
            "192.168.1.1", // RFC 1918
            "169.254.169.254", // link-local — the cloud metadata endpoint
            "fe80::1", // IPv6 link-local
            "100.64.0.1", // CGNAT (RFC 6598) — k8s pod networking
            "192.0.0.1", // IETF protocol assignments
            "192.0.2.1", // TEST-NET-1
            "198.18.0.1", // benchmarking
            "198.51.100.1", // TEST-NET-2
            "203.0.113.1", // TEST-NET-3
            "240.0.0.1", // Class E reserved
            "255.255.255.255", // broadcast
            "224.0.0.1", // multicast
            "fc00::1", // IPv6 unique-local
            "fd12:3456::1", // IPv6 unique-local
            "64:ff9b::7f00:1", // NAT64 well-known prefix embedding 127.0.0.1
            "2001:db8::1", // documentation
        }
    )
    void blocksNonPublicAddresses(String literal) throws UnknownHostException {
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName(literal)))
            .as("%s must be treated as non-public (SSRF-unsafe)", literal)
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "8.8.8.8", // Google DNS
            "1.1.1.1", // Cloudflare DNS
            "140.82.121.4", // github.com range
            "2606:4700:4700::1111", // Cloudflare public IPv6
        }
    )
    void allowsPublicAddresses(String literal) throws UnknownHostException {
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName(literal)))
            .as("%s is public and must be allowed", literal)
            .isFalse();
    }

    @Test
    void cgnatBoundaryIsExactRfc6598Range() throws UnknownHostException {
        // 100.64.0.0/10 = 100.64.0.0 .. 100.127.255.255. 100.63.x and 100.128.x are public.
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName("100.64.0.0"))).isTrue();
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName("100.63.255.255"))).isFalse();
        assertThat(PrivateAddressGuard.isNonPublic(InetAddress.getByName("100.128.0.0"))).isFalse();
    }
}

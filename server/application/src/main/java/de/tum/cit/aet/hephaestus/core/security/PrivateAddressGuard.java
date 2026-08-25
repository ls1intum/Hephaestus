package de.tum.cit.aet.hephaestus.core.security;

import java.net.InetAddress;

/**
 * Single source of truth for "is this resolved IP an SSRF-unsafe target?" — i.e. an address that is
 * loopback/link-local/private or an IANA special-purpose range that routinely fronts internal services.
 *
 * <p>Used both by {@code integration.identity.connect.IssuerDiscoveryProbe} (registration-time issuer
 * probe) and by {@code core.SsrfGuardedResolverGroup} (the HTTP-client DNS layer that closes the
 * rebind/TOCTOU window for outbound calls to user-supplied hosts). Keeping the predicate here — pure,
 * dependency-free, operating on already-resolved {@link InetAddress} bytes — means the two enforcement
 * points can never disagree on what counts as private.
 *
 * <p>Per the OWASP SSRF Prevention Cheat Sheet this covers the SSRF-relevant special-purpose ranges
 * (those that can front internal or loopback-reachable services), not every IANA assignment: purely
 * non-SSRF allocations (6to4-relay anycast, AS112, AMT) are intentionally not blocked.
 */
public final class PrivateAddressGuard {

    private PrivateAddressGuard() {}

    /** True if {@code addr} must NOT be used as an outbound target (SSRF-unsafe). */
    public static boolean isNonPublic(InetAddress addr) {
        return (
            addr.isLoopbackAddress() ||
            addr.isLinkLocalAddress() ||
            addr.isSiteLocalAddress() ||
            addr.isAnyLocalAddress() ||
            addr.isMulticastAddress() ||
            isUniqueLocalIpv6(addr) ||
            isReservedRange(addr)
        );
    }

    /** fc00::/7 — IPv6 unique-local; not covered by {@link InetAddress#isSiteLocalAddress()}. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * IANA special-purpose ranges that {@link InetAddress}'s {@code isXxx()} predicates do NOT flag but
     * which routinely front internal services. {@code isSiteLocalAddress()} is RFC-1918-only, so CGNAT
     * (RFC 6598, standard pod networking in EKS/GKE), benchmarking, TEST-NET, Class-E and especially the
     * NAT64 well-known prefix (RFC 6052 — its low 32 bits can encode IPv4 loopback) all slip past the
     * predicates above. Operates on the already-resolved address bytes (allocation-free, IPv4/IPv6-uniform).
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
}

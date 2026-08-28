package de.tum.cit.aet.hephaestus.core.security;

import java.net.InetAddress;

/** Classifies resolved addresses before outbound connections to user-supplied hosts. */
public final class PrivateAddressGuard {

    private PrivateAddressGuard() {}

    public static boolean isNonPublic(InetAddress addr) {
        return (addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()
                || isUniqueLocalIpv6(addr)
                || isReservedRange(addr));
    }

    /** fc00::/7 — IPv6 unique-local; not covered by {@link InetAddress#isSiteLocalAddress()}. */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /** Additional IANA special-purpose ranges not covered by {@link InetAddress} predicates. */
    private static boolean isReservedRange(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF, b2 = b[2] & 0xFF;
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
            // 64:ff9b::/32 includes standardized NAT64 prefixes.
            if ((b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xff && (b[3] & 0xFF) == 0x9b) {
                return true;
            }
            // 2001:db8::/32 documentation
            return (b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0d && (b[3] & 0xFF) == 0xb8;
        }
        return false;
    }
}

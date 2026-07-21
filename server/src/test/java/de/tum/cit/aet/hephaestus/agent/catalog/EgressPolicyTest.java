package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

/**
 * Table-driven behavioral coverage for {@link EgressPolicy} — the SSRF/credential-leak guard for
 * instance-owned LLM provider connections (#1368 security fix wave). The guard previously shipped with
 * ZERO unit tests; every branch below was exercised by hand against the class body first.
 */
class EgressPolicyTest extends BaseUnitTest {

    @Mock
    private InstanceLlmSettingsRepository settingsRepository;

    private EgressPolicy loopbackBlocked() {
        return new EgressPolicy(settingsRepository, false);
    }

    private EgressPolicy loopbackAllowed() {
        return new EgressPolicy(settingsRepository, true);
    }

    private void stubNoSettingsRow() {
        lenient().when(settingsRepository.findById((short) 1)).thenReturn(java.util.Optional.empty());
    }

    private void stubAllowlist(String allowlist) {
        InstanceLlmSettings settings = new InstanceLlmSettings();
        settings.setId((short) 1);
        settings.setAllowedEgressHosts(allowlist);
        when(settingsRepository.findById((short) 1)).thenReturn(java.util.Optional.of(settings));
    }

    @Nested
    class PrivateAndLinkLocalAddresses {

        @ParameterizedTest
        @DisplayName("cloud metadata endpoint and RFC1918 private ranges are always blocked, allowlist or not")
        @ValueSource(
            strings = {
                "169.254.169.254", // cloud metadata endpoint (link-local)
                "10.0.0.1", // 10/8
                "10.255.255.255",
                "172.16.0.1", // 172.16/12
                "172.31.255.255",
                "192.168.0.1", // 192.168/16
                "192.168.255.255",
                "fc00::1", // IPv6 unique-local
                "fe80::1", // IPv6 link-local
            }
        )
        void blocksPrivateAndLinkLocalHosts(String host) {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackBlocked();

            assertThatThrownBy(() -> policy.validate("https://" + wrapIfIpv6(host) + "/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider host must be a public HTTPS URL");
        }

        @Test
        @DisplayName("a real public IP (8.8.8.8) is not caught by the private-range check — no over-blocking")
        void doesNotOverBlockPublicAddresses() {
            stubAllowlist(null); // blank allowlist = permit any public host

            loopbackBlocked().validate("https://8.8.8.8/v1"); // must not throw
        }
    }

    @Nested
    class Loopback {

        @ParameterizedTest
        @ValueSource(strings = { "localhost", "127.0.0.1" })
        @DisplayName("loopback is blocked by default (hephaestus.llm.egress.allow-loopback=false)")
        void blocksLoopbackByDefault(String host) {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackBlocked();

            assertThatThrownBy(() -> policy.validate("http://" + host + ":11434/v1")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("https to loopback is ALSO blocked by default — the flag gates loopback outright, not just http")
        void blocksHttpsLoopbackByDefaultToo() {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackBlocked();

            assertThatThrownBy(() -> policy.validate("https://127.0.0.1/v1")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @ParameterizedTest
        @ValueSource(strings = { "localhost", "127.0.0.1" })
        @DisplayName("plain http to loopback is allowed when hephaestus.llm.egress.allow-loopback=true")
        void allowsHttpLoopbackWhenFlagEnabled(String host) {
            EgressPolicy policy = loopbackAllowed();

            policy.validate("http://" + host + ":11434/v1"); // must not throw
        }

        @Test
        @DisplayName("::1 (IPv6 loopback literal) is allowed under http when the flag is enabled")
        void allowsIpv6LoopbackWhenFlagEnabled() {
            EgressPolicy policy = loopbackAllowed();

            policy.validate("http://[::1]:11434/v1"); // must not throw
        }
    }

    @Nested
    class SchemeEnforcement {

        @Test
        @DisplayName("plain http to a non-loopback host is rejected regardless of the loopback flag")
        void rejectsHttpToPublicHost() {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackAllowed();

            assertThatThrownBy(() -> policy.validate("http://8.8.8.8/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider host must be a public HTTPS URL");
        }

        @Test
        @DisplayName("a scheme-less / host-less URL is rejected")
        void rejectsMissingHost() {
            assertThatThrownBy(() -> loopbackBlocked().validate("https:///v1")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        void rejectsNullBaseUrl() {
            assertThatThrownBy(() -> loopbackBlocked().validate(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankBaseUrl() {
            assertThatThrownBy(() -> loopbackBlocked().validate("   ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsUnparsableBaseUrl() {
            assertThatThrownBy(() -> loopbackBlocked().validate("not a url at all")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    class CredentialAndQuerySmuggling {

        @Test
        @DisplayName("userinfo in the base URL is rejected (credential smuggled in the URL itself)")
        void rejectsUserinfo() {
            assertThatThrownBy(() -> loopbackBlocked().validate("https://user:secret@gw.example.com/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider URLs must not contain credentials or query parameters.");
        }

        @Test
        @DisplayName("a query string in the base URL is rejected (e.g. gateway URLs with ?api-key=SECRET)")
        void rejectsQueryString() {
            assertThatThrownBy(() -> loopbackBlocked().validate("https://gw.example.com/v1?api-key=SECRET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider URLs must not contain credentials or query parameters.");
        }

        @Test
        @DisplayName("a fragment in the base URL is rejected")
        void rejectsFragment() {
            assertThatThrownBy(() -> loopbackBlocked().validate("https://gw.example.com/v1#token=SECRET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider URLs must not contain credentials or query parameters.");
        }

        @Test
        @DisplayName("a bare path with no query/fragment/userinfo is unaffected by the smuggling guard")
        void allowsPlainPath() {
            // A bare IP literal — no DNS resolution needed, keeping this test deterministic/offline-safe.
            stubAllowlist(null);
            loopbackBlocked().validate("https://8.8.8.8/v1/openai"); // must not throw
        }
    }

    @Nested
    class Allowlist {

        // IP literals throughout: InetAddress.getAllByName() parses a numeric address locally without a
        // DNS round-trip, so these stay deterministic/offline-safe (assertPublicAddress runs — and must
        // succeed — before assertAllowlisted is ever reached).

        @Test
        @DisplayName("an empty/blank allowlist permits any public host")
        void emptyAllowlistPermitsAnyPublicHost() {
            stubAllowlist("");

            loopbackBlocked().validate("https://8.8.8.8/v1"); // must not throw
        }

        @Test
        @DisplayName("no settings row at all (not seeded) behaves like an empty allowlist")
        void missingSettingsRowPermitsAnyPublicHost() {
            stubNoSettingsRow();

            loopbackBlocked().validate("https://8.8.8.8/v1"); // must not throw
        }

        @Test
        @DisplayName("a host present in the allowlist (case-insensitive) is permitted")
        void allowlistHit() {
            // example.com is IANA-reserved and guaranteed globally resolvable — used here (instead of an
            // IP literal) specifically to exercise the allowlist's case-insensitive host match.
            stubAllowlist("api.openai.com,EXAMPLE.COM\napi.anthropic.com");

            loopbackBlocked().validate("https://example.com/v1"); // must not throw
        }

        @Test
        @DisplayName("a host absent from a non-blank allowlist is rejected")
        void allowlistMiss() {
            stubAllowlist("1.1.1.1,9.9.9.9");

            assertThatThrownBy(() -> loopbackBlocked().validate("https://8.8.8.8/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8.8.8.8")
                .hasMessageContaining("not in the allowed list");
        }
    }

    @Nested
    class HostnameResolution {

        @Test
        @DisplayName(
            "a hostname that resolves (via /etc/hosts, no network needed) to a loopback address is still " +
                "blocked by default — the private/loopback check runs on the RESOLVED address, not the literal host"
        )
        void resolvedLoopbackHostnameIsBlocked() {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackBlocked();

            // "localhost" is itself in LOCAL_DEV_HOSTS, but with the flag OFF that short-circuit is
            // disabled entirely, so this exercises the DNS-resolution path (InetAddress.getAllByName)
            // rather than the literal-host allowlist.
            assertThatThrownBy(() -> policy.validate("https://localhost/v1")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("a hostname that fails to resolve is rejected rather than propagating UnknownHostException")
        void unresolvableHostnameIsRejected() {
            stubNoSettingsRow();
            EgressPolicy policy = loopbackBlocked();

            assertThatThrownBy(() -> policy.validate("https://this-host-does-not-exist.invalid.example.test/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider host must be a public HTTPS URL");
        }
    }

    /** Wraps a bare IPv6 literal in brackets for use as a URI host; leaves IPv4/hostnames untouched. */
    private static String wrapIfIpv6(String host) {
        return host.contains(":") ? "[" + host + "]" : host;
    }
}

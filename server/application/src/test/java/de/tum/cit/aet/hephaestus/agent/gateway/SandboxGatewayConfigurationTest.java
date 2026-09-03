package de.tum.cit.aet.hephaestus.agent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.ratelimit.AuthRateLimitProperties;
import de.tum.cit.aet.hephaestus.core.auth.ratelimit.BucketResolver;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.bucket4j.BucketConfiguration;
import java.net.InetAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

class SandboxGatewayConfigurationTest extends BaseUnitTest {

    /**
     * The security-relevant half of the change: sandboxes dial the worker across a per-job Docker
     * bridge, so the gateway must answer on every interface, while the connector serving {@code
     * /api/**} and the management endpoints stays wherever {@code server.address} put it.
     */
    @Test
    void bindsTheGatewayOnEveryInterfaceWhileTheApplicationConnectorStaysWhereServerAddressPutIt() {
        var factory = new TomcatServletWebServerFactory();
        factory.setAddress(InetAddress.getLoopbackAddress());

        new SandboxGatewayConfiguration()
                .sandboxGatewayConnector(new SandboxGatewayProperties(9081, 1024, 60))
                .customize(factory);

        assertThat(factory.getAdditionalConnectors()).singleElement().satisfies(connector -> {
            assertThat(connector.getPort()).isEqualTo(9081);
            assertThat(connector.getProperty("address")).isNull();
        });
        assertThat(factory.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }

    /**
     * The fallback a worker-only pod runs on, where the instance's shared resolver is not wired. Two
     * calls with one key have to reach one bucket, or the limit stops limiting.
     */
    @Test
    void givesOneBucketPerKeyWhenThePodHasNoSharedResolver() {
        BucketResolver resolver = new SandboxGatewayConfiguration().sandboxGatewayBucketResolver();
        BucketConfiguration onePerMinute =
                new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1)).bucketConfiguration();

        assertThat(resolver.resolve("job:1", onePerMinute).tryConsume(1)).isTrue();
        assertThat(resolver.resolve("job:1", onePerMinute).tryConsume(1)).isFalse();
        assertThat(resolver.resolve("job:2", onePerMinute).tryConsume(1)).isTrue();
    }
}

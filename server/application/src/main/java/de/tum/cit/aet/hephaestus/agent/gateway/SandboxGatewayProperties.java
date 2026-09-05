package de.tum.cit.aet.hephaestus.agent.gateway;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hephaestus.sandbox.gateway")
public record SandboxGatewayProperties(
        @DefaultValue("8081") @Min(1) @Max(65535) int port,
        @DefaultValue("4194304") @Min(1) int maxRequestBytes,
        @DefaultValue("120") @Min(1) int requestsPerMinute) {

    /** Where a sandbox reaches the gateway, given the address the worker has on the sandbox's network. */
    public String urlFor(String appServerIp) {
        return "http://" + appServerIp + ":" + port;
    }
}

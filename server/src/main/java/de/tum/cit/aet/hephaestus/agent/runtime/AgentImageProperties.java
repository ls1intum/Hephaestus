package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hephaestus.agent.image")
public record AgentImageProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") String reference,
    @DefaultValue("IF_NOT_PRESENT") ImagePullPolicy pullPolicy
) {}

package de.tum.cit.aet.hephaestus.integration.scm.domain.workdir;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hephaestus.git")
public record GitRepositoryProperties(boolean enabled) {}

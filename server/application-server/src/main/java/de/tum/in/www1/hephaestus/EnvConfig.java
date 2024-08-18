package de.tum.in.www1.hephaestus;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "environment")
public class EnvConfig {

    private String githubPat;

    public EnvConfig(String githubPat) {
        this.githubPat = githubPat;
    }

    public String getGithubPat() {
        return githubPat;
    }
}

package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EnvFileIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void githubPatIsLoadedFromSpringEnvironment() {
        String githubPat = environment.getProperty("GITHUB_PAT");
        assertNotNull(githubPat, "GITHUB_PAT should be present in Spring Environment");
        assertFalse(githubPat.isBlank(), "GITHUB_PAT should not be empty");
    }

}

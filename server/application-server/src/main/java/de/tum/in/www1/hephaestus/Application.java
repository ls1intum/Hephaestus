package de.tum.in.www1.hephaestus;

import de.tum.in.www1.hephaestus.config.GitHubApiPatches;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class Application {
    static {
        GitHubApiPatches.ensureApplied();
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        SpringApplication.run(Application.class, args);
    }
}

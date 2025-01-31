package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BadPracticeDetectorConfig {

    @Value("${hephaestus.intelligence-service.url}")
    private String badPracticeDetectorUrl;

    @Bean
    public BadPracticeDetectorService badPracticeDetectorService() {
        return new BadPracticeDetectorService();
    }

    public class BadPracticeDetectorService extends DetectorApi {

        public BadPracticeDetectorService() {
            super(new ApiClient().setBasePath(badPracticeDetectorUrl));
        }
    }
}

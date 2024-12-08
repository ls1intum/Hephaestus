package de.tum.in.www1.hephaestus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.api.MentorApi;

@Configuration
public class IntelligenceServiceConfig {

    @Value("${hephaestus.intelligence-service.url}")
    private String intelligenceServiceUrl;

    @Bean
    public IntelligenceServiceApi intelligenceServiceApi() {
        return new IntelligenceServiceApi();
    }

    public class IntelligenceServiceApi extends MentorApi {
        public IntelligenceServiceApi() {
            super(new ApiClient().setBasePath(intelligenceServiceUrl));
        }
    }
}

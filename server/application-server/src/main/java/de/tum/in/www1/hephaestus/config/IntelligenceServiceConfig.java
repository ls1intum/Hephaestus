package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DocumentsApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.MentorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.VoteApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntelligenceServiceConfig {

    @Value("${hephaestus.intelligence-service.url}")
    private String intelligenceServiceUrl;

    @Bean
    public ApiClient intelligenceApiClient() {
        return new ApiClient().setBasePath(intelligenceServiceUrl);
    }

    @Bean
    public MentorApi mentorApi(ApiClient intelligenceApiClient) {
        return new MentorApi(intelligenceApiClient);
    }

    @Bean
    public DocumentsApi documentsApi(ApiClient intelligenceApiClient) {
        return new DocumentsApi(intelligenceApiClient);
    }

    @Bean
    public VoteApi voteApi(ApiClient intelligenceApiClient) {
        return new VoteApi(intelligenceApiClient);
    }

    @Bean
    public DetectorApi detectorApi(ApiClient intelligenceApiClient) {
        return new DetectorApi(intelligenceApiClient);
    }
}

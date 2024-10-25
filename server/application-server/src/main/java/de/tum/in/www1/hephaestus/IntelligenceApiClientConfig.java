package de.tum.in.www1.hephaestus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.hephaestus.intelligenceapi.ApiClient;

@Configuration
public class IntelligenceApiClientConfig {
    @Autowired
    public IntelligenceApiClientConfig(ApiClient apiClient) {
        apiClient.setBasePath("http://localhost");
    }
}

package de.tum.in.www1.hephaestus.testconfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Test configuration that provides mock RestTemplate beans to prevent
 * any live HTTP calls during integration tests.
 * <p>
 * <b>CRITICAL</b>: Integration tests must NEVER make live API calls.
 * This configuration ensures all RestTemplate beans used in production
 * are replaced with mocks that return safe defaults.
 */
@TestConfiguration
public class TestRestTemplateConfig {

    /**
     * Mock RestTemplate for GitHub raw content fetching.
     * <p>
     * Returns empty string for all requests to prevent actual HTTP calls
     * to raw.githubusercontent.com during tests.
     */
    @Bean("gitHubRawRestTemplate")
    @Primary
    public RestTemplate gitHubRawRestTemplate() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        // Return empty string for any template fetch - simulates "no template found"
        when(mockRestTemplate.getForObject(anyString(), eq(String.class))).thenReturn("");
        return mockRestTemplate;
    }
}

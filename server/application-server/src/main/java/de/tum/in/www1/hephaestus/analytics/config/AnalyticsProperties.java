package de.tum.in.www1.hephaestus.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hephaestus.analytics")
public class AnalyticsProperties {

    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();

    @Data
    public static class RateLimit {
        private int capacity = 100;
        private int refillTokens = 100;
        private int refillMinutes = 1;
    }

    @Data
    public static class Cache {
        private int teamMetricsTtlMinutes = 15;
        private int analysisResultsTtlMinutes = 30;
        private int recommendationsTtlMinutes = 60;
    }
}
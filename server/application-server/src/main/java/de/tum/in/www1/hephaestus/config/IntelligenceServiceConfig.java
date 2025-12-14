package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DocumentsApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.MentorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.api.VoteApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Intelligence Service API clients.
 *
 * <p>Provides Spring beans for the generated OpenAPI client classes that communicate
 * with the intelligence service. These APIs are grouped as follows:
 *
 * <ul>
 *   <li><b>Mentor APIs</b> - Chat, documents, and voting functionality for the mentor feature:
 *     <ul>
 *       <li>{@link MentorApi} - Chat threads and chat messaging (mentorChatApi)</li>
 *       <li>{@link DocumentsApi} - Document management (mentorDocumentsApi)</li>
 *       <li>{@link VoteApi} - Message voting (mentorVoteApi)</li>
 *     </ul>
 *   </li>
 *   <li><b>Detector API</b> - Bad practice detection for pull requests</li>
 * </ul>
 *
 * <p>Note: The mentor endpoints are also exposed via {@code MentorProxyController} which
 * uses a raw WebClient for SSE streaming support. These beans can be used for server-side
 * operations that don't require streaming.
 */
@Configuration
public class IntelligenceServiceConfig {

    @Value("${hephaestus.intelligence-service.url}")
    private String intelligenceServiceUrl;

    @Bean
    public ApiClient intelligenceApiClient() {
        return new ApiClient().setBasePath(intelligenceServiceUrl);
    }

    // ==================== Mentor APIs ====================

    /**
     * API for mentor chat functionality including threads and messaging.
     * Handles /mentor/threads/* and /mentor/chat endpoints.
     */
    @Bean
    public MentorApi mentorChatApi(ApiClient intelligenceApiClient) {
        return new MentorApi(intelligenceApiClient);
    }

    /**
     * API for mentor document management.
     * Handles /mentor/documents/* endpoints.
     */
    @Bean
    public DocumentsApi mentorDocumentsApi(ApiClient intelligenceApiClient) {
        return new DocumentsApi(intelligenceApiClient);
    }

    /**
     * API for voting on mentor chat messages.
     * Handles /mentor/chat/messages/{messageId}/vote endpoint.
     */
    @Bean
    public VoteApi mentorVoteApi(ApiClient intelligenceApiClient) {
        return new VoteApi(intelligenceApiClient);
    }

    // ==================== Other APIs ====================

    /**
     * API for bad practice detection in pull requests.
     * Handles /detector endpoint.
     */
    @Bean
    public DetectorApi detectorApi(ApiClient intelligenceApiClient) {
        return new DetectorApi(intelligenceApiClient);
    }
}

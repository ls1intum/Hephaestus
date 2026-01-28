package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlPaginationHelper.PaginationRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlPaginationHelper.PaginationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlPaginationHelper.TerminationReason;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link GraphQlPaginationHelper}.
 */
@ExtendWith(MockitoExtension.class)
class GraphQlPaginationHelperTest {

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private HttpGraphQlClient client;

    @Mock
    private HttpGraphQlClient.RequestSpec requestSpec;

    private GraphQlPaginationHelper helper;

    private static final Long SCOPE_ID = 123L;
    private static final String DOCUMENT_NAME = "TestQuery";
    private static final String FIELD_PATH = "repository.items";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @BeforeEach
    void setUp() {
        helper = new GraphQlPaginationHelper(graphQlClientProvider);
    }

    /**
     * Simple test connection class that mimics GraphQL connection types.
     */
    static class TestConnection {
        private final List<String> nodes;
        private final GHPageInfo pageInfo;

        TestConnection(List<String> nodes, GHPageInfo pageInfo) {
            this.nodes = nodes;
            this.pageInfo = pageInfo;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public GHPageInfo getPageInfo() {
            return pageInfo;
        }
    }

    @Nested
    @DisplayName("paginate()")
    class PaginateTests {

        @Test
        @DisplayName("should process single page and complete normally")
        void shouldProcessSinglePageAndComplete() {
            // Given
            GHPageInfo pageInfo = new GHPageInfo("cursor1", false, false, null);
            TestConnection connection = new TestConnection(List.of("item1", "item2"), pageInfo);

            ClientGraphQlResponse response = mockValidResponse(connection);
            mockClientExecution(response);

            AtomicInteger processedCount = new AtomicInteger(0);

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> {
                    processedCount.addAndGet(conn.getNodes().size());
                    return true;
                })
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(1);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.COMPLETED);
            assertThat(result.isComplete()).isTrue();
            assertThat(result.isAborted()).isFalse();
            assertThat(processedCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should paginate through multiple pages")
        void shouldPaginateThroughMultiplePages() {
            // Given
            GHPageInfo pageInfo1 = new GHPageInfo("cursor1", true, false, null);
            GHPageInfo pageInfo2 = new GHPageInfo("cursor2", true, false, null);
            GHPageInfo pageInfo3 = new GHPageInfo("cursor3", false, false, null);

            TestConnection connection1 = new TestConnection(List.of("item1"), pageInfo1);
            TestConnection connection2 = new TestConnection(List.of("item2"), pageInfo2);
            TestConnection connection3 = new TestConnection(List.of("item3"), pageInfo3);

            ClientGraphQlResponse response1 = mockValidResponse(connection1);
            ClientGraphQlResponse response2 = mockValidResponse(connection2);
            ClientGraphQlResponse response3 = mockValidResponse(connection3);

            mockClientExecutionSequence(response1, response2, response3);

            List<String> allItems = new ArrayList<>();

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> {
                    allItems.addAll(conn.getNodes());
                    return true;
                })
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(3);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.COMPLETED);
            assertThat(allItems).containsExactly("item1", "item2", "item3");
        }

        @Test
        @DisplayName("should stop when rate limit is critical")
        void shouldStopWhenRateLimitIsCritical() {
            // Given
            GHPageInfo pageInfo = new GHPageInfo("cursor1", true, false, null);
            TestConnection connection = new TestConnection(List.of("item1"), pageInfo);
            ClientGraphQlResponse response = mockValidResponse(connection);
            mockClientExecution(response);

            when(graphQlClientProvider.isRateLimitCritical(SCOPE_ID)).thenReturn(true);

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> true)
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(1);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.RATE_LIMIT_CRITICAL);
            assertThat(result.isAborted()).isTrue();
        }

        @Test
        @DisplayName("should stop when response is invalid")
        void shouldStopWhenResponseIsInvalid() {
            // Given
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.isValid()).thenReturn(false);
            when(response.getErrors()).thenReturn(List.of());
            mockClientExecution(response);

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> true)
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(1);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.INVALID_RESPONSE);
            assertThat(result.isAborted()).isTrue();
        }

        @Test
        @DisplayName("should stop when connection is null")
        void shouldStopWhenConnectionIsNull() {
            // Given
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            ClientResponseField field = mock(ClientResponseField.class);
            when(response.isValid()).thenReturn(true);
            when(response.field(FIELD_PATH)).thenReturn(field);
            when(field.toEntity(TestConnection.class)).thenReturn(null);
            mockClientExecution(response);

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> true)
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(1);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.NULL_CONNECTION);
        }

        @Test
        @DisplayName("should stop when processor returns false")
        void shouldStopWhenProcessorReturnsFalse() {
            // Given
            GHPageInfo pageInfo = new GHPageInfo("cursor1", true, false, null);
            TestConnection connection = new TestConnection(List.of("item1"), pageInfo);
            ClientGraphQlResponse response = mockValidResponse(connection);
            mockClientExecution(response);

            // When
            PaginationResult result = helper.paginate(
                createRequest(conn -> false) // Processor requests stop
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(1);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.PROCESSOR_STOP);
            assertThat(result.isAborted()).isFalse(); // PROCESSOR_STOP is not considered aborted
        }

        @Test
        @DisplayName("should respect max pages limit")
        void shouldRespectMaxPagesLimit() {
            // Given - always return hasNextPage=true
            GHPageInfo pageInfo = new GHPageInfo("cursor", true, false, null);
            TestConnection connection = new TestConnection(List.of("item"), pageInfo);
            ClientGraphQlResponse response = mockValidResponse(connection);

            // Mock to always return the same response (infinite pagination scenario)
            when(client.documentName(DOCUMENT_NAME)).thenReturn(requestSpec);
            when(requestSpec.variable(any(), any())).thenReturn(requestSpec);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            AtomicInteger pageCount = new AtomicInteger(0);

            // When
            PaginationResult result = helper.paginate(
                PaginationRequest.<TestConnection>builder()
                    .client(client)
                    .scopeId(SCOPE_ID)
                    .documentName(DOCUMENT_NAME)
                    .variables(Map.of("key", "value"))
                    .timeout(TIMEOUT)
                    .connectionFieldPath(FIELD_PATH)
                    .connectionType(TestConnection.class)
                    .pageInfoExtractor(TestConnection::getPageInfo)
                    .pageProcessor(conn -> {
                        pageCount.incrementAndGet();
                        return true;
                    })
                    .contextDescription("test")
                    .maxPages(3) // Limit to 3 pages
                    .build()
            );

            // Then
            assertThat(result.pagesProcessed()).isEqualTo(3);
            assertThat(result.terminationReason()).isEqualTo(TerminationReason.MAX_PAGES_REACHED);
            assertThat(result.isAborted()).isTrue();
            assertThat(pageCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should use initial cursor when provided")
        void shouldUseInitialCursorWhenProvided() {
            // Given
            String initialCursor = "resumeCursor123";
            GHPageInfo pageInfo = new GHPageInfo("cursor1", false, false, null);
            TestConnection connection = new TestConnection(List.of("item1"), pageInfo);
            ClientGraphQlResponse response = mockValidResponse(connection);
            mockClientExecution(response);

            // When
            PaginationResult result = helper.paginate(
                PaginationRequest.<TestConnection>builder()
                    .client(client)
                    .scopeId(SCOPE_ID)
                    .documentName(DOCUMENT_NAME)
                    .variables(Map.of("key", "value"))
                    .timeout(TIMEOUT)
                    .connectionFieldPath(FIELD_PATH)
                    .connectionType(TestConnection.class)
                    .pageInfoExtractor(TestConnection::getPageInfo)
                    .pageProcessor(conn -> true)
                    .contextDescription("test")
                    .initialCursor(initialCursor)
                    .build()
            );

            // Then
            assertThat(result.isComplete()).isTrue();
            // Verify the initial cursor was used
            verify(requestSpec).variable("after", initialCursor);
        }

        @Test
        @DisplayName("should track rate limit for each page")
        void shouldTrackRateLimitForEachPage() {
            // Given
            GHPageInfo pageInfo1 = new GHPageInfo("cursor1", true, false, null);
            GHPageInfo pageInfo2 = new GHPageInfo("cursor2", false, false, null);

            TestConnection connection1 = new TestConnection(List.of("item1"), pageInfo1);
            TestConnection connection2 = new TestConnection(List.of("item2"), pageInfo2);

            ClientGraphQlResponse response1 = mockValidResponse(connection1);
            ClientGraphQlResponse response2 = mockValidResponse(connection2);

            mockClientExecutionSequence(response1, response2);

            // When
            helper.paginate(createRequest(conn -> true));

            // Then
            verify(graphQlClientProvider, times(2)).trackRateLimit(eq(SCOPE_ID), any(ClientGraphQlResponse.class));
        }
    }

    @Nested
    @DisplayName("PaginationRequest.Builder")
    class BuilderTests {

        @Test
        @DisplayName("should throw when required fields are missing")
        void shouldThrowWhenRequiredFieldsAreMissing() {
            assertThatThrownBy(() -> PaginationRequest.<TestConnection>builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client is required");
        }

        @Test
        @DisplayName("should use default max pages when not specified")
        void shouldUseDefaultMaxPagesWhenNotSpecified() {
            // Given
            GHPageInfo pageInfo = new GHPageInfo(null, false, false, null);
            TestConnection connection = new TestConnection(List.of("item"), pageInfo);
            ClientGraphQlResponse response = mockValidResponse(connection);
            mockClientExecution(response);

            PaginationRequest<TestConnection> request = createRequest(conn -> true);

            // Then - default is MAX_PAGINATION_PAGES (1000)
            assertThat(request.maxPages()).isEqualTo(GitHubSyncConstants.MAX_PAGINATION_PAGES);
        }
    }

    @Nested
    @DisplayName("PaginationResult")
    class PaginationResultTests {

        @Test
        @DisplayName("isComplete should return true only for COMPLETED reason")
        void isCompleteShouldReturnTrueOnlyForCompleted() {
            assertThat(new PaginationResult(1, TerminationReason.COMPLETED).isComplete()).isTrue();
            assertThat(new PaginationResult(1, TerminationReason.MAX_PAGES_REACHED).isComplete()).isFalse();
            assertThat(new PaginationResult(1, TerminationReason.RATE_LIMIT_CRITICAL).isComplete()).isFalse();
        }

        @Test
        @DisplayName("isAborted should return false for COMPLETED and PROCESSOR_STOP")
        void isAbortedShouldReturnFalseForCompletedAndProcessorStop() {
            assertThat(new PaginationResult(1, TerminationReason.COMPLETED).isAborted()).isFalse();
            assertThat(new PaginationResult(1, TerminationReason.PROCESSOR_STOP).isAborted()).isFalse();
            assertThat(new PaginationResult(1, TerminationReason.MAX_PAGES_REACHED).isAborted()).isTrue();
            assertThat(new PaginationResult(1, TerminationReason.RATE_LIMIT_CRITICAL).isAborted()).isTrue();
        }
    }

    // Helper methods

    private PaginationRequest<TestConnection> createRequest(GraphQlPaginationHelper.PageProcessor<TestConnection> processor) {
        return PaginationRequest.<TestConnection>builder()
            .client(client)
            .scopeId(SCOPE_ID)
            .documentName(DOCUMENT_NAME)
            .variables(Map.of("key", "value"))
            .timeout(TIMEOUT)
            .connectionFieldPath(FIELD_PATH)
            .connectionType(TestConnection.class)
            .pageInfoExtractor(TestConnection::getPageInfo)
            .pageProcessor(processor)
            .contextDescription("test")
            .build();
    }

    private ClientGraphQlResponse mockValidResponse(TestConnection connection) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        ClientResponseField field = mock(ClientResponseField.class);
        when(response.isValid()).thenReturn(true);
        when(response.field(FIELD_PATH)).thenReturn(field);
        when(field.toEntity(TestConnection.class)).thenReturn(connection);
        return response;
    }

    private void mockClientExecution(ClientGraphQlResponse response) {
        when(client.documentName(DOCUMENT_NAME)).thenReturn(requestSpec);
        when(requestSpec.variable(any(), any())).thenReturn(requestSpec);
        when(requestSpec.execute()).thenReturn(Mono.just(response));
    }

    private void mockClientExecutionSequence(ClientGraphQlResponse... responses) {
        when(client.documentName(DOCUMENT_NAME)).thenReturn(requestSpec);
        when(requestSpec.variable(any(), any())).thenReturn(requestSpec);

        // Set up sequential responses
        Mono<ClientGraphQlResponse> first = Mono.just(responses[0]);
        @SuppressWarnings("unchecked")
        Mono<ClientGraphQlResponse>[] rest = new Mono[responses.length - 1];
        for (int i = 1; i < responses.length; i++) {
            rest[i - 1] = Mono.just(responses[i]);
        }
        when(requestSpec.execute()).thenReturn(first, rest);
    }
}

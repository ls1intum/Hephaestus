package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldTextValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValueConnection;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient.RequestSpec;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@DisplayName("GitHub Project Item Field Value Sync Service")
@Tag("unit")
class GitHubProjectItemFieldValueSyncServiceTest extends BaseUnitTest {

    @Mock
    private ProjectFieldRepository projectFieldRepository;

    @Mock
    private ProjectFieldValueRepository projectFieldValueRepository;

    @Mock
    private ProjectItemRepository projectItemRepository;

    @Mock
    private GitHubGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitHubSyncProperties syncProperties;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private HttpGraphQlClient graphQlClient;

    @Mock
    private RequestSpec requestSpec;

    @Mock
    private ClientGraphQlResponse clientGraphQlResponse;

    @Mock
    private ClientResponseField fieldValuesField;

    @Test
    @DisplayName("should clear field values when empty list is final")
    void shouldClearFieldValuesWhenEmptyListIsFinal() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        service.processFieldValues(42L, List.of(), false, null);

        verify(projectFieldValueRepository).deleteAllByItemId(42L);
    }

    @Test
    @DisplayName("should not delete field values when truncated")
    void shouldNotDeleteFieldValuesWhenTruncated() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        GitHubProjectFieldValueDTO dto = new GitHubProjectFieldValueDTO(
            "field-1",
            "TEXT",
            "value",
            null,
            null,
            null,
            null
        );
        when(projectFieldRepository.existsById("field-1")).thenReturn(true);

        service.processFieldValues(42L, List.of(dto), true, "cursor");

        verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(eq(42L), any());
        verify(projectFieldValueRepository, never()).deleteAllByItemId(42L);
    }

    @Test
    @DisplayName("should remove stale values after completing pagination")
    void shouldRemoveStaleValuesAfterPagination() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
        when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
        when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.execute()).thenReturn(Mono.just(clientGraphQlResponse));
        when(clientGraphQlResponse.isValid()).thenReturn(true);
        when(clientGraphQlResponse.field("node.fieldValues")).thenReturn(fieldValuesField);
        GHProjectV2ItemFieldValueConnection connection = new GHProjectV2ItemFieldValueConnection();
        connection.setNodes(List.of());
        connection.setPageInfo(new GHPageInfo());
        when(fieldValuesField.toEntity(GHProjectV2ItemFieldValueConnection.class)).thenReturn(connection);

        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of("field-1"));

        verify(projectFieldValueRepository).deleteByItemIdAndFieldIdNotIn(eq(42L), eq(List.of("field-1")));
    }

    @Test
    @DisplayName("should accumulate field IDs across multiple pages for stale cleanup")
    void shouldAccumulateFieldIdsAcrossMultiplePages() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        // Given - Mock GraphQL client chain
        when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
        when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
        when(syncProperties.paginationThrottle()).thenReturn(Duration.ZERO);
        when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

        // Page 1: fields {field-1, field-2}, has next page
        GHProjectV2ItemFieldValueConnection page1 = new GHProjectV2ItemFieldValueConnection();
        var textValue1 = new GHProjectV2ItemFieldTextValue();
        GHProjectV2Field field1Obj = new GHProjectV2Field();
        field1Obj.setId("field-1");
        textValue1.setField(field1Obj);
        var textValue2 = new GHProjectV2ItemFieldTextValue();
        GHProjectV2Field field2Obj = new GHProjectV2Field();
        field2Obj.setId("field-2");
        textValue2.setField(field2Obj);
        page1.setNodes(List.of(textValue1, textValue2));
        GHPageInfo pageInfo1 = new GHPageInfo("cursor1", true, false, null);
        page1.setPageInfo(pageInfo1);

        // Page 2: fields {field-3, field-4}, no next page
        GHProjectV2ItemFieldValueConnection page2 = new GHProjectV2ItemFieldValueConnection();
        var textValue3 = new GHProjectV2ItemFieldTextValue();
        GHProjectV2Field field3Obj = new GHProjectV2Field();
        field3Obj.setId("field-3");
        textValue3.setField(field3Obj);
        var textValue4 = new GHProjectV2ItemFieldTextValue();
        GHProjectV2Field field4Obj = new GHProjectV2Field();
        field4Obj.setId("field-4");
        textValue4.setField(field4Obj);
        page2.setNodes(List.of(textValue3, textValue4));
        GHPageInfo pageInfo2 = new GHPageInfo(null, false, false, null);
        page2.setPageInfo(pageInfo2);

        ClientGraphQlResponse response1 = mock(ClientGraphQlResponse.class);
        ClientResponseField field1 = mock(ClientResponseField.class);
        when(response1.isValid()).thenReturn(true);
        when(response1.field("node.fieldValues")).thenReturn(field1);
        when(field1.toEntity(GHProjectV2ItemFieldValueConnection.class)).thenReturn(page1);

        ClientGraphQlResponse response2 = mock(ClientGraphQlResponse.class);
        ClientResponseField field2 = mock(ClientResponseField.class);
        when(response2.isValid()).thenReturn(true);
        when(response2.field("node.fieldValues")).thenReturn(field2);
        when(field2.toEntity(GHProjectV2ItemFieldValueConnection.class)).thenReturn(page2);

        when(requestSpec.execute()).thenReturn(Mono.just(response1), Mono.just(response2));

        when(projectItemRepository.existsById(42L)).thenReturn(true);
        when(projectFieldRepository.existsById(anyString())).thenReturn(true);

        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        })
            .when(transactionTemplate)
            .executeWithoutResult(any());

        // When
        service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of());

        // Then - verify stale cleanup receives accumulated field IDs from BOTH pages
        verify(projectFieldValueRepository).deleteByItemIdAndFieldIdNotIn(
            eq(42L),
            argThat(
                fieldIds ->
                    fieldIds.containsAll(List.of("field-1", "field-2", "field-3", "field-4")) && fieldIds.size() == 4
            )
        );
    }

    @Test
    @DisplayName("should return 0 and make no GraphQL calls for null itemNodeId")
    void shouldReturnZeroForNullItemNodeId() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        int result = service.syncRemainingFieldValues(123L, null, 42L, null, List.of());

        assertThat(result).isZero();
        verify(graphQlClientProvider, never()).forScope(any());
    }

    @Test
    @DisplayName("should skip stale cleanup when pagination is aborted")
    void shouldSkipStaleCleanupWhenPaginationAborted() {
        GitHubProjectItemFieldValueSyncService service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate
        );

        // Given - rate limit becomes critical immediately, so the code path is:
        // 1. Execute GraphQL query → valid response
        // 2. trackRateLimit() called
        // 3. isRateLimitCritical() → true → break (transaction never runs)
        when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
        when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
        when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

        // Minimal valid response — the code breaks before extracting the connection
        when(clientGraphQlResponse.isValid()).thenReturn(true);
        when(requestSpec.execute()).thenReturn(Mono.just(clientGraphQlResponse));

        // Rate limit is critical → breaks before connection extraction / transaction
        when(graphQlClientProvider.isRateLimitCritical(123L)).thenReturn(true);

        // When
        service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of());

        // Then - stale cleanup should NOT be called because pagination didn't complete normally
        verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(any(), any());
        verify(projectFieldValueRepository, never()).deleteAllByItemId(any());
    }
}

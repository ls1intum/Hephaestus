package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
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
}

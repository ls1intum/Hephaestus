package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Field;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldTextValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValueConnection;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectField;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    private GitHubExceptionClassifier exceptionClassifier;

    @Mock
    private HttpGraphQlClient graphQlClient;

    @Mock
    private RequestSpec requestSpec;

    @Mock
    private ClientGraphQlResponse clientGraphQlResponse;

    @Mock
    private ClientResponseField fieldValuesField;

    private GitHubProjectItemFieldValueSyncService service;

    @BeforeEach
    void setUp() {
        // Default exception classifier stub to prevent NPEs on unexpected exceptions
        lenient()
            .when(exceptionClassifier.classifyWithDetails(any()))
            .thenReturn(ClassificationResult.of(Category.UNKNOWN, "test error"));

        service = new GitHubProjectItemFieldValueSyncService(
            projectFieldRepository,
            projectFieldValueRepository,
            projectItemRepository,
            graphQlClientProvider,
            syncProperties,
            transactionTemplate,
            exceptionClassifier
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // processFieldValues tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processFieldValues")
    class ProcessFieldValues {

        @Test
        @DisplayName("should return empty list when itemId is null")
        void shouldReturnEmptyListWhenItemIdIsNull() {
            // Act
            List<String> result = service.processFieldValues(null, List.of(), false, null);

            // Assert
            assertThat(result).isEmpty();
            verifyNoInteractions(projectFieldRepository);
            verifyNoInteractions(projectFieldValueRepository);
        }

        @Test
        @DisplayName("should clear field values when empty list is final")
        void shouldClearFieldValuesWhenEmptyListIsFinal() {
            // Act
            service.processFieldValues(42L, List.of(), false, null);

            // Assert
            verify(projectFieldValueRepository).deleteAllByItemId(42L);
        }

        @Test
        @DisplayName("should not delete field values when truncated with empty list")
        void shouldNotDeleteFieldValuesWhenTruncatedWithEmptyList() {
            // Act
            service.processFieldValues(42L, List.of(), true, "cursor");

            // Assert
            verify(projectFieldValueRepository, never()).deleteAllByItemId(any());
            verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(any(), any());
        }

        @Test
        @DisplayName("should clear field values when null list is final")
        void shouldClearFieldValuesWhenNullListIsFinal() {
            // Act
            service.processFieldValues(42L, null, false, null);

            // Assert
            verify(projectFieldValueRepository).deleteAllByItemId(42L);
        }

        @Test
        @DisplayName("should upsert single text field value and return field ID")
        void shouldUpsertSingleTextFieldValueAndReturnFieldId() {
            // Arrange
            GitHubProjectFieldValueDTO dto = new GitHubProjectFieldValueDTO(
                "field-1",
                "TEXT",
                "hello world",
                null,
                null,
                null,
                null
            );
            when(projectFieldRepository.existsById("field-1")).thenReturn(true);

            // Act
            List<String> result = service.processFieldValues(42L, List.of(dto), false, null);

            // Assert
            assertThat(result).containsExactly("field-1");
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-1"),
                eq("hello world"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should upsert multiple field values of different types")
        void shouldUpsertMultipleFieldValuesOfDifferentTypes() {
            // Arrange
            GitHubProjectFieldValueDTO textDto = new GitHubProjectFieldValueDTO(
                "field-text",
                "TEXT",
                "some text",
                null,
                null,
                null,
                null
            );
            GitHubProjectFieldValueDTO numberDto = new GitHubProjectFieldValueDTO(
                "field-number",
                "NUMBER",
                null,
                42.5,
                null,
                null,
                null
            );
            GitHubProjectFieldValueDTO dateDto = new GitHubProjectFieldValueDTO(
                "field-date",
                "DATE",
                null,
                null,
                LocalDate.of(2025, 6, 15),
                null,
                null
            );
            GitHubProjectFieldValueDTO singleSelectDto = new GitHubProjectFieldValueDTO(
                "field-ss",
                "SINGLE_SELECT",
                null,
                null,
                null,
                "option-abc",
                null
            );
            GitHubProjectFieldValueDTO iterationDto = new GitHubProjectFieldValueDTO(
                "field-iter",
                "ITERATION",
                null,
                null,
                null,
                null,
                "iteration-xyz"
            );

            when(projectFieldRepository.existsById(anyString())).thenReturn(true);

            List<GitHubProjectFieldValueDTO> fieldValues = List.of(
                textDto,
                numberDto,
                dateDto,
                singleSelectDto,
                iterationDto
            );

            // Act
            List<String> result = service.processFieldValues(42L, fieldValues, false, null);

            // Assert
            assertThat(result).containsExactly("field-text", "field-number", "field-date", "field-ss", "field-iter");

            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-text"),
                eq("some text"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-number"),
                isNull(),
                eq(42.5),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-date"),
                isNull(),
                isNull(),
                eq(LocalDate.of(2025, 6, 15)),
                isNull(),
                isNull(),
                any(Instant.class)
            );
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-ss"),
                isNull(),
                isNull(),
                isNull(),
                eq("option-abc"),
                isNull(),
                any(Instant.class)
            );
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-iter"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("iteration-xyz"),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should skip field value when field not found in repository")
        void shouldSkipFieldValueWhenFieldNotFoundInRepository() {
            // Arrange
            GitHubProjectFieldValueDTO knownField = new GitHubProjectFieldValueDTO(
                "field-known",
                "TEXT",
                "value",
                null,
                null,
                null,
                null
            );
            GitHubProjectFieldValueDTO unknownField = new GitHubProjectFieldValueDTO(
                "field-unknown",
                "TEXT",
                "value",
                null,
                null,
                null,
                null
            );

            when(projectFieldRepository.existsById("field-known")).thenReturn(true);
            when(projectFieldRepository.existsById("field-unknown")).thenReturn(false);

            // Act
            List<String> result = service.processFieldValues(42L, List.of(knownField, unknownField), false, null);

            // Assert
            assertThat(result).containsExactly("field-known");
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-known"),
                eq("value"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
            // Should NOT upsert the unknown field
            verify(projectFieldValueRepository, never()).upsertCore(
                eq(42L),
                eq("field-unknown"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should skip null field values in list")
        void shouldSkipNullFieldValuesInList() {
            // Arrange
            GitHubProjectFieldValueDTO validDto = new GitHubProjectFieldValueDTO(
                "field-1",
                "TEXT",
                "value",
                null,
                null,
                null,
                null
            );
            when(projectFieldRepository.existsById("field-1")).thenReturn(true);

            List<GitHubProjectFieldValueDTO> fieldValues = new java.util.ArrayList<>();
            fieldValues.add(null);
            fieldValues.add(validDto);
            fieldValues.add(new GitHubProjectFieldValueDTO(null, "TEXT", "val", null, null, null, null));

            // Act
            List<String> result = service.processFieldValues(42L, fieldValues, false, null);

            // Assert
            assertThat(result).containsExactly("field-1");
        }

        @Test
        @DisplayName("should not delete field values when truncated")
        void shouldNotDeleteFieldValuesWhenTruncated() {
            // Arrange
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

            // Act
            service.processFieldValues(42L, List.of(dto), true, "cursor");

            // Assert
            verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(eq(42L), any());
            verify(projectFieldValueRepository, never()).deleteAllByItemId(42L);
        }

        @Test
        @DisplayName("should remove stale field values when not truncated")
        void shouldRemoveStaleFieldValuesWhenNotTruncated() {
            // Arrange
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

            // Act
            service.processFieldValues(42L, List.of(dto), false, null);

            // Assert
            verify(projectFieldValueRepository).deleteByItemIdAndFieldIdNotIn(eq(42L), eq(List.of("field-1")));
        }

        @Test
        @DisplayName("should process labels field value with JSON text")
        void shouldProcessLabelsFieldValueWithJsonText() {
            // Arrange — labels are serialized as JSON text
            GitHubProjectFieldValueDTO labelsDto = new GitHubProjectFieldValueDTO(
                "field-labels",
                "LABELS",
                "[\"bug\",\"enhancement\"]",
                null,
                null,
                null,
                null
            );
            when(projectFieldRepository.existsById("field-labels")).thenReturn(true);

            // Act
            List<String> result = service.processFieldValues(42L, List.of(labelsDto), false, null);

            // Assert
            assertThat(result).containsExactly("field-labels");
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-labels"),
                eq("[\"bug\",\"enhancement\"]"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should process reviewers field value with JSON text")
        void shouldProcessReviewersFieldValueWithJsonText() {
            // Arrange — reviewers are serialized as JSON text
            GitHubProjectFieldValueDTO reviewersDto = new GitHubProjectFieldValueDTO(
                "field-reviewers",
                "REVIEWERS",
                "[\"user1\",\"team-alpha\"]",
                null,
                null,
                null,
                null
            );
            when(projectFieldRepository.existsById("field-reviewers")).thenReturn(true);

            // Act
            List<String> result = service.processFieldValues(42L, List.of(reviewersDto), false, null);

            // Assert
            assertThat(result).containsExactly("field-reviewers");
            verify(projectFieldValueRepository).upsertCore(
                eq(42L),
                eq("field-reviewers"),
                eq("[\"user1\",\"team-alpha\"]"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // upsertFieldDefinition tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("upsertFieldDefinition")
    class UpsertFieldDefinition {

        @Test
        @DisplayName("should create new field definition with all properties")
        void shouldCreateNewFieldDefinitionWithAllProperties() {
            // Arrange
            Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2025-06-15T12:00:00Z");
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_field1",
                "Status",
                "SINGLE_SELECT",
                List.of(
                    new GitHubProjectFieldDTO.Option("opt-1", "Todo", "GREEN", "Not started"),
                    new GitHubProjectFieldDTO.Option("opt-2", "Done", "PURPLE", "Completed")
                ),
                createdAt,
                updatedAt
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_field1"),
                eq(10L),
                eq("Status"),
                eq("SINGLE_SELECT"),
                argThat(json -> json != null && json.contains("opt-1") && json.contains("Todo")),
                eq(createdAt),
                eq(updatedAt)
            );
        }

        @Test
        @DisplayName("should default to TEXT data type when null")
        void shouldDefaultToTextDataTypeWhenNull() {
            // Arrange
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_field2",
                "Custom Field",
                null,
                Collections.emptyList(),
                Instant.now(),
                Instant.now()
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_field2"),
                eq(10L),
                eq("Custom Field"),
                eq("TEXT"),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should use current time when timestamps are null")
        void shouldUseCurrentTimeWhenTimestampsAreNull() {
            // Arrange
            Instant before = Instant.now();
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_field3",
                "Priority",
                "NUMBER",
                Collections.emptyList(),
                null,
                null
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_field3"),
                eq(10L),
                eq("Priority"),
                eq("NUMBER"),
                isNull(),
                argThat(instant -> !instant.isBefore(before)),
                argThat(instant -> !instant.isBefore(before))
            );
        }

        @Test
        @DisplayName("should pass null options when empty list")
        void shouldPassNullOptionsWhenEmptyList() {
            // Arrange
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_field4",
                "Notes",
                "TEXT",
                Collections.emptyList(),
                Instant.now(),
                Instant.now()
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_field4"),
                eq(10L),
                eq("Notes"),
                eq("TEXT"),
                isNull(),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should handle iteration field type with options")
        void shouldHandleIterationFieldTypeWithOptions() {
            // Arrange
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_iter",
                "Sprint",
                "ITERATION",
                List.of(
                    new GitHubProjectFieldDTO.Option("iter-1", "Sprint 1", null, null),
                    new GitHubProjectFieldDTO.Option("iter-2", "Sprint 2", null, null)
                ),
                Instant.now(),
                Instant.now()
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_iter"),
                eq(10L),
                eq("Sprint"),
                eq("ITERATION"),
                argThat(json -> json != null && json.contains("iter-1") && json.contains("Sprint 1")),
                any(Instant.class),
                any(Instant.class)
            );
        }

        @Test
        @DisplayName("should handle update of existing field definition")
        void shouldHandleUpdateOfExistingFieldDefinition() {
            // Arrange — upsertCore handles insert-or-update at DB level;
            // calling it twice with the same ID simulates an update
            Instant updatedAt = Instant.parse("2025-07-01T00:00:00Z");
            GitHubProjectFieldDTO fieldDto = new GitHubProjectFieldDTO(
                "PVTF_existing",
                "Renamed Field",
                "DATE",
                Collections.emptyList(),
                Instant.parse("2025-01-01T00:00:00Z"),
                updatedAt
            );

            // Act
            service.upsertFieldDefinition(fieldDto, 10L);

            // Assert
            verify(projectFieldRepository).upsertCore(
                eq("PVTF_existing"),
                eq(10L),
                eq("Renamed Field"),
                eq("DATE"),
                isNull(),
                eq(Instant.parse("2025-01-01T00:00:00Z")),
                eq(updatedAt)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // removeStaleFieldDefinitions tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeStaleFieldDefinitions")
    class RemoveStaleFieldDefinitions {

        @Test
        @DisplayName("should remove fields not seen during sync")
        void shouldRemoveFieldsNotSeenDuringSync() {
            // Arrange
            when(projectFieldRepository.deleteByProjectIdAndIdNotIn(eq(10L), any())).thenReturn(3);

            // Act
            int removed = service.removeStaleFieldDefinitions(10L, List.of("field-a", "field-b"));

            // Assert
            assertThat(removed).isEqualTo(3);
            verify(projectFieldRepository).deleteByProjectIdAndIdNotIn(eq(10L), eq(List.of("field-a", "field-b")));
        }

        @Test
        @DisplayName("should return 0 when all fields are still present")
        void shouldReturnZeroWhenAllFieldsStillPresent() {
            // Arrange
            when(projectFieldRepository.deleteByProjectIdAndIdNotIn(eq(10L), any())).thenReturn(0);

            // Act
            int removed = service.removeStaleFieldDefinitions(10L, List.of("field-a", "field-b", "field-c"));

            // Assert
            assertThat(removed).isZero();
            verify(projectFieldRepository).deleteByProjectIdAndIdNotIn(
                eq(10L),
                eq(List.of("field-a", "field-b", "field-c"))
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // syncRemainingFieldValues tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncRemainingFieldValues")
    class SyncRemainingFieldValues {

        @Test
        @DisplayName("should return 0 and make no GraphQL calls for null itemNodeId")
        void shouldReturnZeroForNullItemNodeId() {
            // Act
            int result = service.syncRemainingFieldValues(123L, null, 42L, null, List.of());

            // Assert
            assertThat(result).isZero();
            verify(graphQlClientProvider, never()).forScope(any());
        }

        @Test
        @DisplayName("should return 0 for blank itemNodeId")
        void shouldReturnZeroForBlankItemNodeId() {
            // Act
            int result = service.syncRemainingFieldValues(123L, "  ", 42L, null, List.of());

            // Assert
            assertThat(result).isZero();
            verify(graphQlClientProvider, never()).forScope(any());
        }

        @Test
        @DisplayName("should return 0 for null itemId")
        void shouldReturnZeroForNullItemId() {
            // Act
            int result = service.syncRemainingFieldValues(123L, "node-id", null, null, List.of());

            // Assert
            assertThat(result).isZero();
            verify(graphQlClientProvider, never()).forScope(any());
        }

        @Test
        @DisplayName("should remove stale values after completing pagination")
        void shouldRemoveStaleValuesAfterPagination() {
            // Arrange
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

            // Act
            service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of("field-1"));

            // Assert
            verify(projectFieldValueRepository).deleteByItemIdAndFieldIdNotIn(eq(42L), eq(List.of("field-1")));
        }

        @Test
        @DisplayName("should accumulate field IDs across multiple pages for stale cleanup")
        void shouldAccumulateFieldIdsAcrossMultiplePages() {
            // Arrange — Mock GraphQL client chain
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

            // Act
            service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of());

            // Assert — verify stale cleanup receives accumulated field IDs from BOTH pages
            verify(projectFieldValueRepository).deleteByItemIdAndFieldIdNotIn(
                eq(42L),
                argThat(
                    fieldIds ->
                        fieldIds.containsAll(List.of("field-1", "field-2", "field-3", "field-4")) &&
                        fieldIds.size() == 4
                )
            );
        }

        @Test
        @DisplayName("should skip stale cleanup when pagination is aborted")
        void shouldSkipStaleCleanupWhenPaginationAborted() throws InterruptedException {
            // Arrange — rate limit becomes critical immediately
            when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
            when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
            when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

            when(clientGraphQlResponse.isValid()).thenReturn(true);
            when(requestSpec.execute()).thenReturn(Mono.just(clientGraphQlResponse));

            when(graphQlClientProvider.isRateLimitCritical(123L)).thenReturn(true);
            when(graphQlClientProvider.waitIfRateLimitLow(123L)).thenThrow(new InterruptedException("rate limit"));

            // Act
            service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of());

            // Assert — stale cleanup should NOT be called
            verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(any(), any());
            verify(projectFieldValueRepository, never()).deleteAllByItemId(any());
        }

        @Test
        @DisplayName("should skip item when it no longer exists in DB during pagination")
        void shouldSkipItemWhenNotFoundDuringPagination() {
            // Arrange
            when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
            when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
            when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

            // Page with one field value
            GHProjectV2ItemFieldValueConnection connection = new GHProjectV2ItemFieldValueConnection();
            var textValue = new GHProjectV2ItemFieldTextValue();
            GHProjectV2Field fieldObj = new GHProjectV2Field();
            fieldObj.setId("field-1");
            textValue.setField(fieldObj);
            connection.setNodes(List.of(textValue));
            GHPageInfo pageInfo = new GHPageInfo(null, false, false, null);
            connection.setPageInfo(pageInfo);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            ClientResponseField responseField = mock(ClientResponseField.class);
            when(response.isValid()).thenReturn(true);
            when(response.field("node.fieldValues")).thenReturn(responseField);
            when(responseField.toEntity(GHProjectV2ItemFieldValueConnection.class)).thenReturn(connection);
            when(requestSpec.execute()).thenReturn(Mono.just(response));

            // Item does not exist in DB
            when(projectItemRepository.existsById(42L)).thenReturn(false);

            doAnswer(invocation -> {
                Consumer<TransactionStatus> action = invocation.getArgument(0);
                action.accept(null);
                return null;
            })
                .when(transactionTemplate)
                .executeWithoutResult(any());

            // Act
            int result = service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of());

            // Assert — node count is still added but no upsert happens
            assertThat(result).isEqualTo(1);
            verify(projectFieldValueRepository, never()).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("should break on invalid GraphQL response")
        void shouldBreakOnInvalidGraphQlResponse() {
            // Arrange
            when(graphQlClientProvider.forScope(123L)).thenReturn(graphQlClient);
            when(syncProperties.graphqlTimeout()).thenReturn(Duration.ofSeconds(5));
            when(graphQlClient.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

            when(clientGraphQlResponse.isValid()).thenReturn(false);
            when(clientGraphQlResponse.getErrors()).thenReturn(List.of());
            when(requestSpec.execute()).thenReturn(Mono.just(clientGraphQlResponse));

            // Act
            int result = service.syncRemainingFieldValues(123L, "item-node", 42L, null, List.of("field-1"));

            // Assert
            assertThat(result).isZero();
            verify(projectFieldValueRepository, never()).upsertCore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
            // Should NOT clean up stale values since pagination didn't complete
            verify(projectFieldValueRepository, never()).deleteByItemIdAndFieldIdNotIn(any(), any());
        }
    }
}

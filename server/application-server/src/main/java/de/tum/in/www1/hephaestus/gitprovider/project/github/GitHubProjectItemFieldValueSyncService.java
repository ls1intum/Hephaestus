package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.FIELD_VALUES_PAGINATION_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValueConnection;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectField;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectFieldValueRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectFieldValueDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class GitHubProjectItemFieldValueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectItemFieldValueSyncService.class);
    private static final String GET_PROJECT_ITEM_FIELD_VALUES_DOCUMENT = "GetProjectItemFieldValues";

    private static final int TRANSPORT_MAX_RETRIES = 3;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);
    private static final double JITTER_FACTOR = 0.5;

    private final ProjectFieldRepository projectFieldRepository;
    private final ProjectFieldValueRepository projectFieldValueRepository;
    private final ProjectItemRepository projectItemRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubSyncProperties syncProperties;
    private final TransactionTemplate transactionTemplate;

    public GitHubProjectItemFieldValueSyncService(
        ProjectFieldRepository projectFieldRepository,
        ProjectFieldValueRepository projectFieldValueRepository,
        ProjectItemRepository projectItemRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubSyncProperties syncProperties,
        TransactionTemplate transactionTemplate
    ) {
        this.projectFieldRepository = projectFieldRepository;
        this.projectFieldValueRepository = projectFieldValueRepository;
        this.projectItemRepository = projectItemRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncProperties = syncProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public List<String> processFieldValues(
        Long itemId,
        List<GitHubProjectFieldValueDTO> fieldValues,
        boolean truncated,
        String endCursor
    ) {
        if (itemId == null) {
            return List.of();
        }

        if (fieldValues == null || fieldValues.isEmpty()) {
            if (!truncated) {
                deleteAllFieldValues(itemId);
            }
            return List.of();
        }

        List<String> processedFieldIds = new ArrayList<>();

        for (GitHubProjectFieldValueDTO fieldValue : fieldValues) {
            if (fieldValue == null || fieldValue.fieldId() == null) {
                continue;
            }

            if (!projectFieldRepository.existsById(fieldValue.fieldId())) {
                log.debug(
                    "Skipped field value: reason=fieldNotFound, fieldId={}, itemId={}",
                    fieldValue.fieldId(),
                    itemId
                );
                continue;
            }

            processedFieldIds.add(fieldValue.fieldId());

            projectFieldValueRepository.upsertCore(
                itemId,
                fieldValue.fieldId(),
                fieldValue.textValue(),
                fieldValue.numberValue(),
                fieldValue.dateValue(),
                fieldValue.singleSelectOptionId(),
                fieldValue.iterationId(),
                Instant.now()
            );
        }

        if (!truncated) {
            removeStaleFieldValues(itemId, processedFieldIds);
        }

        return processedFieldIds;
    }

    public int syncRemainingFieldValues(
        Long scopeId,
        String itemNodeId,
        Long itemId,
        String startCursor,
        List<String> initialFieldIds
    ) {
        if (itemNodeId == null || itemNodeId.isBlank() || itemId == null) {
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        boolean hasMore = true;
        String cursor = startCursor;
        int pageCount = 0;
        int totalSynced = 0;
        boolean completedNormally = false;
        List<String> processedFieldIds = new ArrayList<>();
        if (initialFieldIds != null) {
            processedFieldIds.addAll(initialFieldIds);
        }

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for item field values: itemId={}, limit={}",
                    itemId,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;

                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_PROJECT_ITEM_FIELD_VALUES_DOCUMENT)
                        .variable("itemId", itemNodeId)
                        .variable("first", FIELD_VALUES_PAGINATION_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying field values pagination after transport error: itemId={}, page={}, attempt={}, error={}",
                                    itemId,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(timeout);

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response for item field values: itemId={}, errors={}",
                        itemId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting field values pagination due to critical rate limit: itemId={}", itemId);
                    break;
                }

                GHProjectV2ItemFieldValueConnection fieldValuesConnection = graphQlResponse
                    .field("node.fieldValues")
                    .toEntity(GHProjectV2ItemFieldValueConnection.class);

                if (fieldValuesConnection == null || fieldValuesConnection.getNodes() == null) {
                    break;
                }

                List<GitHubProjectFieldValueDTO> fieldValueDTOs = fieldValuesConnection
                    .getNodes()
                    .stream()
                    .map(GitHubProjectFieldValueDTO::fromFieldValue)
                    .filter(dto -> dto != null && dto.fieldId() != null)
                    .toList();

                transactionTemplate.executeWithoutResult(status -> {
                    if (!projectItemRepository.existsById(itemId)) {
                        log.debug("Skipped field values: reason=itemNotFound, itemId={}", itemId);
                        return;
                    }

                    for (GitHubProjectFieldValueDTO fieldValue : fieldValueDTOs) {
                        if (!projectFieldRepository.existsById(fieldValue.fieldId())) {
                            log.debug(
                                "Skipped field value: reason=fieldNotFound, fieldId={}, itemId={}",
                                fieldValue.fieldId(),
                                itemId
                            );
                            continue;
                        }

                        processedFieldIds.add(fieldValue.fieldId());

                        projectFieldValueRepository.upsertCore(
                            itemId,
                            fieldValue.fieldId(),
                            fieldValue.textValue(),
                            fieldValue.numberValue(),
                            fieldValue.dateValue(),
                            fieldValue.singleSelectOptionId(),
                            fieldValue.iterationId(),
                            Instant.now()
                        );
                    }
                });

                totalSynced += fieldValuesConnection.getNodes().size();

                var pageInfo = fieldValuesConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                if (!hasMore) {
                    completedNormally = true;
                }

                if (fieldValuesConnection.getNodes().isEmpty()) {
                    completedNormally = true;
                    break;
                }

                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Field values pagination interrupted during throttle: itemId={}", itemId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to sync remaining field values: itemId={}, error={}", itemId, e.getMessage());
                break;
            }
        }

        if (!processedFieldIds.isEmpty() && completedNormally) {
            removeStaleFieldValues(itemId, processedFieldIds);
        }

        if (totalSynced > 0) {
            log.debug("Synced remaining field values: itemId={}, count={}, pages={}", itemId, totalSynced, pageCount);
        }

        return totalSynced;
    }

    /**
     * Upserts a project field definition from a DTO.
     * <p>
     * Delegates to {@link ProjectFieldRepository#upsertCore} so callers do not need
     * a direct dependency on the field repository.
     *
     * @param fieldDto  the field DTO containing id, name, data type, options, and timestamps
     * @param projectId the owning project's database ID
     */
    @Transactional
    public void upsertFieldDefinition(GitHubProjectFieldDTO fieldDto, Long projectId) {
        ProjectField.DataType dataType = fieldDto.getDataTypeEnum();
        if (dataType == null) {
            dataType = ProjectField.DataType.TEXT;
        }
        Instant createdAt = fieldDto.createdAt() != null ? fieldDto.createdAt() : Instant.now();
        Instant updatedAt = fieldDto.updatedAt() != null ? fieldDto.updatedAt() : Instant.now();

        projectFieldRepository.upsertCore(
            fieldDto.id(),
            projectId,
            fieldDto.name(),
            dataType.name(),
            fieldDto.getOptionsJson(),
            createdAt,
            updatedAt
        );
    }

    /**
     * Removes project field definitions that were not seen during the current sync cycle.
     *
     * @param projectId      the project whose fields to clean up
     * @param syncedFieldIds IDs of fields that were synced (should be kept)
     * @return the number of stale fields removed
     */
    @Transactional
    public int removeStaleFieldDefinitions(Long projectId, Collection<String> syncedFieldIds) {
        return projectFieldRepository.deleteByProjectIdAndIdNotIn(projectId, List.copyOf(syncedFieldIds));
    }

    private void deleteAllFieldValues(Long itemId) {
        int removed = projectFieldValueRepository.deleteAllByItemId(itemId);
        if (removed > 0) {
            log.debug("Removed field values: itemId={}, count={}", itemId, removed);
        }
    }

    private void removeStaleFieldValues(Long itemId, List<String> processedFieldIds) {
        if (processedFieldIds == null || processedFieldIds.isEmpty()) {
            deleteAllFieldValues(itemId);
            return;
        }

        int removed = projectFieldValueRepository.deleteByItemIdAndFieldIdNotIn(itemId, processedFieldIds);
        if (removed > 0) {
            log.debug("Removed stale field values: itemId={}, count={}", itemId, removed);
        }
    }

    private boolean isTransportError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (className.contains("PrematureCloseException")) {
                return true;
            }
            if (className.contains("AbortedException") || className.contains("ConnectionResetException")) {
                return true;
            }
            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (
                        lower.contains("connection reset") ||
                        lower.contains("broken pipe") ||
                        lower.contains("connection abort") ||
                        lower.contains("premature") ||
                        lower.contains("stream closed")
                    ) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}

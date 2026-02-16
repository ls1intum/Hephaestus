package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.FIELD_VALUES_PAGINATION_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
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
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ProjectFieldRepository projectFieldRepository;
    private final ProjectFieldValueRepository projectFieldValueRepository;
    private final ProjectItemRepository projectItemRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubSyncProperties syncProperties;
    private final TransactionTemplate transactionTemplate;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubProjectItemFieldValueSyncService(
        ProjectFieldRepository projectFieldRepository,
        ProjectFieldValueRepository projectFieldValueRepository,
        ProjectItemRepository projectItemRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubSyncProperties syncProperties,
        TransactionTemplate transactionTemplate,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.projectFieldRepository = projectFieldRepository;
        this.projectFieldValueRepository = projectFieldValueRepository;
        this.projectItemRepository = projectItemRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncProperties = syncProperties;
        this.transactionTemplate = transactionTemplate;
        this.exceptionClassifier = exceptionClassifier;
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
        int retryAttempt = 0;
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
                            .filter(GitHubTransportErrors::isTransportError)
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
                    ClassificationResult classification = classifyGraphQlErrors(graphQlResponse);
                    if (classification != null) {
                        if (
                            handleClassification(
                                classification,
                                "field values pagination",
                                "itemId",
                                itemId,
                                retryAttempt
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Received invalid GraphQL response for item field values: itemId={}, errors={}",
                        itemId,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (!waitForRateLimitIfNeeded(scopeId, "field values pagination", "itemId", itemId)) {
                        break;
                    }
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
                retryAttempt = 0;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (!handleClassification(classification, "field values pagination", "itemId", itemId, retryAttempt)) {
                    break;
                }
                retryAttempt++;
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

    private ClassificationResult classifyGraphQlErrors(ClientGraphQlResponse response) {
        ClassificationResult classification = exceptionClassifier.classifyGraphQlResponse(response);
        if (classification != null) {
            return classification;
        }

        GitHubGraphQlErrorUtils.TransientError transientError = GitHubGraphQlErrorUtils.detectTransientError(response);
        if (transientError == null) {
            return null;
        }

        return switch (transientError.type()) {
            case RATE_LIMIT -> ClassificationResult.rateLimited(
                transientError.getRecommendedWait(),
                "GraphQL rate limit: " + transientError.message()
            );
            case TIMEOUT, SERVER_ERROR -> ClassificationResult.of(
                Category.RETRYABLE,
                "GraphQL transient error: " + transientError.message()
            );
            case RESOURCE_LIMIT -> ClassificationResult.of(
                Category.CLIENT_ERROR,
                "GraphQL resource limit: " + transientError.message()
            );
        };
    }

    private boolean handleClassification(
        ClassificationResult classification,
        String phase,
        String scopeLabel,
        Object scopeValue,
        int retryAttempt
    ) {
        Category category = classification.category();

        switch (category) {
            case RETRYABLE -> {
                if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                    log.warn(
                        "Retrying {} after transient error: {}={}, attempt={}, error={}",
                        phase,
                        scopeLabel,
                        scopeValue,
                        retryAttempt + 1,
                        classification.message()
                    );
                    try {
                        ExponentialBackoff.sleep(retryAttempt + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                }
                log.warn(
                    "Aborting {} after {} retries: {}={}, error={}",
                    phase,
                    MAX_RETRY_ATTEMPTS,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case RATE_LIMITED -> {
                if (retryAttempt < MAX_RETRY_ATTEMPTS && classification.suggestedWait() != null) {
                    long waitMs = Math.min(classification.suggestedWait().toMillis(), 300_000);
                    log.warn(
                        "Rate limited during {}, waiting: {}={}, waitMs={}",
                        phase,
                        scopeLabel,
                        scopeValue,
                        waitMs
                    );
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                }
                log.warn(
                    "Aborting {} due to rate limit: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case NOT_FOUND -> {
                log.warn(
                    "Resource not found during {}: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case AUTH_ERROR -> {
                log.warn(
                    "Authentication error during {}: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case CLIENT_ERROR -> {
                log.warn(
                    "Client error during {}: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            default -> {
                log.warn(
                    "Aborting {} due to error: {}={}, category={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    category,
                    classification.message()
                );
                return false;
            }
        }
    }

    private boolean waitForRateLimitIfNeeded(Long scopeId, String phase, String scopeLabel, Object scopeValue) {
        try {
            boolean waited = graphQlClientProvider.waitIfRateLimitLow(scopeId);
            if (waited) {
                log.info("Paused due to critical rate limit: phase={}, {}={}", phase, scopeLabel, scopeValue);
            }
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for rate limit reset: phase={}, {}={}", phase, scopeLabel, scopeValue);
            return false;
        }
    }
}

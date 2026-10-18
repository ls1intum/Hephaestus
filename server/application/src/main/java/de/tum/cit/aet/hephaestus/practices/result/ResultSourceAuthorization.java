package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultProofAccess;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultSourceAuthorization implements ResultProofAccess {
    private final ProductItemQuery items;
    private final ReviewItemRepository assignments;
    private final HistoryCustodyQuery custody;
    private final ResultRepository results;
    private final JsonMapper json;
    private final ClasspathSourceCatalogRegistry sources;
    private final PersonProcessingSuppression suppression;
    private final PersonIdentityResolver people;
    private final AccountDirectory accounts;
    private final ProblemRepository problems;
    private final ProductItemRepository itemRows;
    private final List<ItemSourceAccess> access;

    public HistoryCustody custody(Result result) {
        var producer = assignments
                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                        result.getWorkspaceId(), result.getReviewId(), result.getPracticeId(), result.getItemId())
                .filter(m -> result.getId().equals(m.getResultId())
                        || (result.getEvidenceVersion() == 0
                                && m.getState() == ReviewItemState.WITHHELD
                                && ReviewItem.LEGACY_AMBIGUOUS_RESULTS.equals(m.getReason())));
        var retained = assignments
                .findByWorkspaceIdAndResultIdOrderByReviewIdAscPracticeIdAscItemIdAsc(
                        result.getWorkspaceId(), result.getId())
                .stream()
                .map(m -> m.getDetail())
                .filter(Objects::nonNull)
                .map(detail -> detail.path("custody"))
                .filter(node -> node.isObject())
                .map(node -> json.convertValue(node, HistoryCustody.class))
                .distinct()
                .toList();
        if (retained.size() == 1) return retained.getFirst();
        if (retained.size() > 1) return new HistoryCustody(false, true, List.of(), List.of(), List.of());
        var job = producer.map(m -> m.getJobId()).orElse(null);
        if (job == null) return new HistoryCustody(false, false, List.of(), List.of(), List.of());
        return custody.find(result.getWorkspaceId(), List.of(job));
    }

    public boolean authorized(Result result, HistoryCustody facts, SourceUsePurpose purpose) {
        if (!facts.complete()) return false;
        if (!sourceAuthorized(result, purpose)) return false;
        return custodyAuthorized(result, facts, purpose);
    }

    private boolean custodyAuthorized(Result result, HistoryCustody facts, SourceUsePurpose purpose) {
        if (facts.refused()) return false;
        if (facts.sourceJobIds().stream().anyMatch(suppression::isReviewJobSuppressed)) return false;
        for (var copy : facts.copiedIdentities()) {
            var providers = people.providers().stream()
                    .filter(p -> p.type().equals(copy.providerType())
                            && p.serverUrl().equals(copy.providerOrigin()))
                    .toList();
            if (providers.size() != 1) return false;
            var provider = providers.getFirst();
            if (suppression.isSuppressed(provider.id(), copy.subject(), copy.teamId())) return false;
            // Capture admits a contributor without an account, so only a linked owner adds controls.
            // The exact closure includes disabled links, which the enabled-link query cannot see.
            final Optional<Long> accountId;
            try {
                accountId = people.accountFor(new PersonIdentity(provider.id(), copy.subject(), copy.teamId()));
            } catch (ResponseStatusException unattributable) {
                return false;
            }
            if (accountId.isPresent()) {
                var account = accounts.find(accountId.orElseThrow());
                if (account.isEmpty()
                        || (account.orElseThrow().status() != Account.Status.ACTIVE
                                && account.orElseThrow().status() != Account.Status.UNCLAIMED)
                        || suppression.isAccountSuppressed(account.orElseThrow().id())
                        || account.orElseThrow().aiChoice() == AiChoice.NO_AI) return false;
            }
        }
        for (var repositoryId : facts.repositoryIds()) {
            var repositories = itemRows.activeRepositoryIds(result.getWorkspaceId(), repositoryId);
            if (repositories.size() != 1
                    || !source(items.require(result.getWorkspaceId(), repositories.getFirst()), purpose)) return false;
        }
        return true;
    }

    /** Migrated words are readable history, never a certificate of complete copy custody. */
    @Override
    @Transactional(readOnly = true)
    public boolean permitsHistory(long workspaceId, UUID resultId, SourceUsePurpose purpose) {
        if (purpose != SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW
                && purpose != SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY) return false;
        return results.findByIdAndWorkspaceId(resultId, workspaceId)
                .map(result -> {
                    var facts = custody(result);
                    if (result.getEvidenceVersion() != 0) return authorized(result, facts, purpose);
                    if (suppression.isReviewJobSuppressed(result.getReviewId())) return false;
                    var developerId = result.getDeveloperId();
                    if (developerId != null) {
                        var developer = accounts.find(developerId);
                        if (developer.isEmpty()
                                || suppression.isAccountSuppressed(developerId)
                                || developer.orElseThrow().aiChoice() == AiChoice.NO_AI
                                || (developer.orElseThrow().status() != Account.Status.ACTIVE
                                        && developer.orElseThrow().status() != Account.Status.UNCLAIMED)) return false;
                    }
                    return visibleSource(result, purpose) && custodyAuthorized(result, facts, purpose);
                })
                .orElse(false);
    }

    private boolean sourceAuthorized(Result result, SourceUsePurpose purpose) {
        if (!visibleSource(result, purpose)) return false;
        var citations = ResultCitations.all(
                result,
                problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(result.getWorkspaceId(), result.getId()));
        try {
            for (var citation : citations)
                if (!citation.path("sourceKind").isString()
                        || !sources.isSourceUsePermitted(
                                new SourceKind(citation.path("sourceKind").asString()), purpose)) return false;
        } catch (IllegalArgumentException unavailable) {
            return false;
        }
        return true;
    }

    private boolean visibleSource(Result result, SourceUsePurpose purpose) {
        var item = items.find(result.getWorkspaceId(), result.getItemId());
        return item.isPresent() && source(item.orElseThrow(), purpose);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean permits(long workspaceId, UUID resultId, SourceUsePurpose purpose) {
        return results.findByIdAndWorkspaceId(resultId, workspaceId)
                .map(result -> authorized(result, custody(result), purpose))
                .orElse(false);
    }

    private boolean source(ItemRef item, SourceUsePurpose purpose) {
        if (item.sourceId() != null
                && suppression.isSourceSuppressed(
                        item.workspaceId(), item.kind().value(), item.sourceId())) return false;
        if (item.parentId() != null) {
            var parent = items.find(item.workspaceId(), item.parentId());
            if (parent.isEmpty() || !source(parent.orElseThrow(), purpose)) return false;
        }
        var owners = access.stream()
                .filter(owner -> owner.kinds().contains(item.kind()))
                .toList();
        return owners.size() == 1 && owners.getFirst().permits(item, purpose);
    }
}

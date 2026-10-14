package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
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
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultProofAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
    private final AccountIdentityQuery identities;
    private final AccountDirectory accounts;
    private final ProblemRepository problems;
    private final JdbcClient jdbc;
    private final List<ItemSourceAccess> access;

    public HistoryCustody custody(Result result) {
        var producer = assignments
                .findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                        result.getWorkspaceId(), result.getReviewId(), result.getPracticeId(), result.getItemId())
                .filter(m -> result.getId().equals(m.getResultId()));
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
        if (retained.size() > 1) return new HistoryCustody(false, List.of(), List.of(), List.of());
        var job = producer.map(m -> m.getJobId()).orElse(null);
        if (job == null) return new HistoryCustody(false, List.of(), List.of(), List.of());
        return custody.find(result.getWorkspaceId(), List.of(job));
    }

    public boolean authorized(Result result, HistoryCustody facts, SourceUsePurpose purpose) {
        if (!facts.complete()) return false;
        var item = items.find(result.getWorkspaceId(), result.getItemId());
        if (item.isEmpty() || !source(item.orElseThrow(), purpose)) return false;
        if (facts.sourceJobIds().stream().anyMatch(suppression::isReviewJobSuppressed)) return false;
        for (var copy : facts.copiedIdentities()) {
            var providers = people.providers().stream()
                    .filter(p -> p.type().equals(copy.providerType())
                            && p.serverUrl().equals(copy.providerOrigin()))
                    .toList();
            if (providers.size() != 1) return false;
            var provider = providers.getFirst();
            if (suppression.isSuppressed(provider.id(), copy.subject(), copy.teamId())) return false;
            var account = identities
                    .resolveAccountId(provider.id(), copy.subject(), copy.teamId())
                    .flatMap(accounts::find);
            if (account.isEmpty()
                    || (account.orElseThrow().status() != Account.Status.ACTIVE
                            && account.orElseThrow().status() != Account.Status.UNCLAIMED)
                    || suppression.isAccountSuppressed(account.orElseThrow().id())
                    || account.orElseThrow().aiChoice() == AiChoice.NO_AI) return false;
        }
        for (var repositoryId : facts.repositoryIds()) {
            var repositories = jdbc.sql("SELECT id FROM item WHERE workspace_id=:ws AND kind='scm.repository' AND"
                            + " source_id=:source AND removed_at IS NULL")
                    .param("ws", result.getWorkspaceId())
                    .param("source", repositoryId)
                    .query((rs, n) -> rs.getLong(1))
                    .list();
            if (repositories.size() != 1
                    || !source(items.require(result.getWorkspaceId(), repositories.getFirst()), purpose)) return false;
        }
        var citations = new ArrayList<>(ResultCitations.all(result));
        problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(result.getWorkspaceId(), result.getId())
                .forEach(p -> p.getCitations().forEach(citations::add));
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

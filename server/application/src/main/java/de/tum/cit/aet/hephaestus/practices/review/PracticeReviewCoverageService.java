package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewScopeRepositoryDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.WorkspaceReviewScopeDTO;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryTarget;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeRepositoryRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipReviewSelection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeReviewCoverageService {
    static final int ESTIMATE_WINDOW_DAYS = 30;
    private final RepositoryToMonitorRepository monitorRepository;
    private final ReviewScopeRepositoryRepository repositoryTargetRepository;
    private final PersonProcessingSuppression suppression;
    private final JdbcClient jdbc;
    private final MonitoredRepositoryQuery monitored;
    private final MembershipReviewSelection selections;

    public Map<String, List<String>> generatedPaths(Workspace workspace) {
        var indexed = monitored.repositories(workspace.getId()).values().stream()
                .collect(Collectors.toMap(ref -> ref.monitorId(), ref -> ref.repositoryItemId()));
        return monitorRepository.findByWorkspaceId(workspace.getId()).stream()
                .filter(m ->
                        indexed.containsKey(m.getId()) && !m.getGeneratedPaths().isEmpty())
                .collect(Collectors.toUnmodifiableMap(
                        m -> Long.toString(Objects.requireNonNull(indexed.get(m.getId()))),
                        m -> List.copyOf(m.getGeneratedPaths())));
    }

    @Transactional
    public void patchGeneratedPaths(Workspace workspace, Map<String, List<String>> requested) {
        var indexed = monitored.repositories(workspace.getId());
        var monitors = monitorRepository.findByWorkspaceId(workspace.getId()).stream()
                .collect(Collectors.toMap(RepositoryToMonitor::getId, Function.identity()));
        var validated = new HashMap<Long, List<String>>();
        requested.forEach((key, patterns) -> {
            long itemId = positiveId(key);
            var repository = indexed.get(itemId);
            if (repository == null || !monitors.containsKey(repository.monitorId()))
                throw new InvalidReviewScopeException("Select a repository this workspace monitors.");
            validated.put(repository.monitorId(), GeneratedPaths.normalize(patterns));
        });
        validated.forEach(
                (id, patterns) -> Objects.requireNonNull(monitors.get(id)).setGeneratedPaths(patterns));
    }

    public static long positiveId(String value) {
        if (!value.matches("[1-9][0-9]*"))
            throw new InvalidReviewScopeException("Use a positive account or repository item id.");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidReviewScopeException("Use a positive account or repository item id.", invalid);
        }
    }

    public Set<Long> eligibleAccountIds(long workspaceId) {
        return jdbc.sql("""
            SELECT m.account_id FROM membership m JOIN account a ON a.id=m.account_id
            WHERE m.workspace_id=:ws AND m.left_at IS NULL AND NOT m.hidden
              AND a.status IN ('ACTIVE','UNCLAIMED') AND (a.ai_choice IS NULL OR a.ai_choice <> 'NO_AI')
            """).param("ws", workspaceId).query((rs, n) -> rs.getLong(1)).list().stream()
                .filter(id -> !suppression.isAccountSuppressed(id))
                .collect(Collectors.toUnmodifiableSet());
    }

    public WorkspaceReviewScope scope(Workspace workspace) {
        var indexed = monitored.repositories(workspace.getId()).values().stream()
                .collect(Collectors.toMap(ref -> ref.monitorId(), ref -> ref.repositoryItemId()));
        var repositories = repositoryTargetRepository.findByWorkspaceId(workspace.getId()).stream()
                .filter(t -> indexed.containsKey(t.getRepositoryMonitorId()))
                .map(t -> new ReviewRepositoryTarget(
                        Objects.requireNonNull(indexed.get(t.getRepositoryMonitorId())), t.getBaseBranches()))
                .sorted(Comparator.comparingLong(ReviewRepositoryTarget::repositoryItemId))
                .toList();
        List<Long> selected = jdbc.sql(
                        "SELECT account_id FROM membership WHERE workspace_id=:ws AND review_selected ORDER BY"
                                + " account_id")
                .param("ws", workspace.getId())
                .query((rs, n) -> rs.getLong(1))
                .list();
        var s = workspace.getReviewSettings();
        return new WorkspaceReviewScope(
                s.getReviewScopeRepositories(), s.getReviewScopePeople(), repositories, selected);
    }

    public WorkspaceReviewScopeDTO wireScope(Workspace workspace) {
        var internal = scope(workspace);
        return new WorkspaceReviewScopeDTO(
                internal.repositoryMode(),
                internal.personMode(),
                internal.repositories().stream()
                        .map(t -> new ReviewScopeRepositoryDTO(Long.toString(t.repositoryItemId()), t.baseBranches()))
                        .toList(),
                internal.accountIds().stream().map(Object::toString).toList());
    }

    public WorkspaceReviewScope fromWire(long workspaceId, WorkspaceReviewScopeDTO dto) {
        var indexed = monitored.repositories(workspaceId);
        var repositories = dto.selectedRepositories().stream()
                .map(t -> {
                    long itemId = positiveId(t.repositoryItemId());
                    if (!indexed.containsKey(itemId))
                        throw new InvalidReviewScopeException("Select a repository this workspace monitors.");
                    return new ReviewRepositoryTarget(itemId, t.baseBranches());
                })
                .toList();
        return new WorkspaceReviewScope(
                dto.repositories(),
                dto.people(),
                repositories,
                dto.accountIds().stream()
                        .map(PracticeReviewCoverageService::positiveId)
                        .toList());
    }

    public boolean admitsRepositoryItem(Workspace workspace, long itemId) {
        var repository = monitored.repositories(workspace.getId()).get(itemId);
        return repository != null && scope(workspace).admitsRepository(itemId, null, false);
    }

    public PracticeReviewScopeSummaryDTO summary(Workspace workspace, int volume) {
        return summary(workspace, scope(workspace), volume);
    }

    private PracticeReviewScopeSummaryDTO summary(Workspace workspace, WorkspaceReviewScope scope, int volume) {
        int monitored = monitorRepository.findByWorkspaceId(workspace.getId()).size(),
                eligible = eligibleAccountIds(workspace.getId()).size();
        return new PracticeReviewScopeSummaryDTO(
                scope.repositoryMode() == ReviewScopeMode.ALL
                        ? monitored
                        : scope.repositories().size(),
                monitored,
                scope.personMode() == ReviewScopeMode.ALL
                        ? eligible
                        : scope.accountIds().size(),
                eligible,
                volume,
                ESTIMATE_WINDOW_DAYS);
    }

    public PracticeReviewScopePreviewDTO preview(Workspace workspace, WorkspaceReviewScope proposed, int volume) {
        validate(workspace.getId(), proposed);
        var before = scope(workspace);
        boolean widens = eligibleAccountIds(workspace.getId()).stream()
                .anyMatch(id -> proposed.admitsPerson(id) && !before.admitsPerson(id));
        for (var repository : monitored.repositories(workspace.getId()).values()) {
            var old = before.repositories().stream()
                    .filter(t -> t.repositoryItemId() == repository.repositoryItemId())
                    .findFirst()
                    .orElse(null);
            var next = proposed.repositories().stream()
                    .filter(t -> t.repositoryItemId() == repository.repositoryItemId())
                    .findFirst()
                    .orElse(null);
            boolean oldAll = before.repositoryMode() == ReviewScopeMode.ALL
                    || (old != null && old.baseBranches().isEmpty());
            boolean nextAll = proposed.repositoryMode() == ReviewScopeMode.ALL
                    || (next != null && next.baseBranches().isEmpty());
            if ((nextAll && !oldAll)
                    || (next != null
                            && !oldAll
                            && (old == null || !old.baseBranches().containsAll(next.baseBranches())))) widens = true;
        }
        return new PracticeReviewScopePreviewDTO(
                summary(workspace, before, volume), summary(workspace, proposed, volume), widens);
    }

    private void validate(long workspaceId, WorkspaceReviewScope requested) {
        var indexed = monitored.repositories(workspaceId);
        if (requested.repositories().stream().anyMatch(t -> !indexed.containsKey(t.repositoryItemId())))
            throw new InvalidReviewScopeException("Select only repositories this workspace monitors.");
        if (!eligibleAccountIds(workspaceId).containsAll(requested.accountIds()))
            throw new InvalidReviewScopeException("Select only eligible workspace members.");
    }

    public boolean admits(
            Workspace workspace,
            @Nullable Long repository,
            @Nullable String branch,
            @Nullable Long developerId,
            boolean branchRestrictionsApply) {
        return (developerId == null
                        || (eligibleAccountIds(workspace.getId()).contains(developerId)
                                && scope(workspace).admitsPerson(developerId)))
                && (repository == null
                        || scope(workspace).admitsRepository(repository, branch, branchRestrictionsApply));
    }

    public @Nullable Long repositoryItemId(long workspaceId, long itemId) {
        return jdbc.sql("WITH RECURSIVE ancestry AS (SELECT id,kind,parent_id FROM item WHERE workspace_id=:ws AND"
                        + " id=:item UNION SELECT parent.id,parent.kind,parent.parent_id FROM item parent JOIN"
                        + " ancestry child ON child.parent_id=parent.id WHERE parent.workspace_id=:ws) SELECT id"
                        + " FROM ancestry WHERE kind='scm.repository'")
                .param("ws", workspaceId)
                .param("item", itemId)
                .query((rs, n) -> rs.getLong(1))
                .optional()
                .orElse(null);
    }

    @Transactional
    public void replace(Workspace workspace, WorkspaceReviewScope requested) {
        validate(workspace.getId(), requested);
        var indexed = monitored.repositories(workspace.getId());
        var desired = new HashMap<Long, List<String>>();
        if (requested.repositoryMode() == ReviewScopeMode.SELECTED)
            requested
                    .repositories()
                    .forEach(t -> desired.put(
                            Objects.requireNonNull(indexed.get(t.repositoryItemId()))
                                    .monitorId(),
                            t.baseBranches()));
        for (var target : repositoryTargetRepository.findByWorkspaceId(workspace.getId())) {
            var branches = desired.remove(target.getRepositoryMonitorId());
            if (branches == null) repositoryTargetRepository.delete(target);
            else target.setBaseBranches(branches);
        }
        desired.forEach((id, branches) ->
                repositoryTargetRepository.save(new ReviewScopeRepository(workspace.getId(), id, branches)));
        selections.replaceSelection(
                workspace.getId(),
                requested.personMode() == ReviewScopeMode.SELECTED ? requested.accountIds() : List.of());
        workspace.getReviewSettings().applyRollout(requested.repositoryMode(), requested.personMode(), null);
    }
}

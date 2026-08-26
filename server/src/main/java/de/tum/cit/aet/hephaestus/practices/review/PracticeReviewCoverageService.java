package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewSubject;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewPersonTarget;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewPersonTargetRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewRepositoryTarget;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewRepositoryTargetRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryTarget;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeReviewCoverageService {

    static final int ESTIMATE_WINDOW_DAYS = 30;

    private final RepositoryToMonitorRepository monitorRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final PracticeReviewRepositoryTargetRepository repositoryTargetRepository;
    private final PracticeReviewPersonTargetRepository personTargetRepository;

    @Transactional(readOnly = true)
    public WorkspaceReviewScope scope(Workspace workspace) {
        return readScope(workspace);
    }

    private WorkspaceReviewScope readScope(Workspace workspace) {
        long workspaceId = workspace.getId();
        List<PracticeReviewRepositoryTarget> targets = repositoryTargetRepository.findByWorkspaceId(workspaceId);
        Map<Long, RepositoryToMonitor> monitorsById = monitorRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .collect(Collectors.toMap(RepositoryToMonitor::getId, Function.identity()));
        List<ReviewRepositoryTarget> repositories = targets
            .stream()
            .map(target -> {
                RepositoryToMonitor monitor = monitorsById.get(target.getRepositoryMonitorId());
                return monitor == null
                    ? null
                    : new ReviewRepositoryTarget(monitor.getNameWithOwner(), target.getBaseBranches());
            })
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(ReviewRepositoryTarget::nameWithOwner))
            .toList();
        List<Long> people = personTargetRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .map(PracticeReviewPersonTarget::getUserId)
            .sorted()
            .toList();
        return new WorkspaceReviewScope(
            workspace.getReviewSettings().getRepositoryCoverageMode(),
            workspace.getReviewSettings().getPersonCoverageMode(),
            repositories,
            people
        );
    }

    @Transactional(readOnly = true)
    public PracticeReviewCoverageSummaryDTO summary(Workspace workspace, int recentReviewVolume) {
        return summary(workspace, readScope(workspace), recentReviewVolume);
    }

    @Transactional(readOnly = true)
    public PracticeReviewCoveragePreviewDTO preview(
        Workspace workspace,
        WorkspaceReviewScope proposed,
        int recentReviewVolume
    ) {
        validate(workspace.getId(), proposed);
        WorkspaceReviewScope current = readScope(workspace);
        return new PracticeReviewCoveragePreviewDTO(
            summary(workspace, current, recentReviewVolume),
            summary(workspace, proposed, recentReviewVolume),
            widens(workspace.getId(), current, proposed)
        );
    }

    private PracticeReviewCoverageSummaryDTO summary(
        Workspace workspace,
        WorkspaceReviewScope scope,
        int recentReviewVolume
    ) {
        int monitored = monitorRepository.findByWorkspaceId(workspace.getId()).size();
        int eligible = eligibleMemberships(workspace.getId()).size();
        int coveredRepositories =
            scope.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED ? monitored : scope.repositories().size();
        int coveredPeople =
            scope.personMode() == ReviewPersonMode.ALL_ELIGIBLE ? eligible : scope.personUserIds().size();
        return new PracticeReviewCoverageSummaryDTO(
            coveredRepositories,
            monitored,
            coveredPeople,
            eligible,
            recentReviewVolume,
            ESTIMATE_WINDOW_DAYS
        );
    }

    private void validate(long workspaceId, WorkspaceReviewScope requested) {
        Set<String> monitored = monitorRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .collect(Collectors.toSet());
        for (ReviewRepositoryTarget repository : requested.repositories()) {
            if (!monitored.contains(repository.nameWithOwner())) {
                throw new InvalidReviewCoverageException(
                    "Repository is not monitored by this workspace: " + repository.nameWithOwner()
                );
            }
        }
        Set<Long> eligible = eligibleMemberships(workspaceId)
            .stream()
            .map(WorkspaceMembership::getUserId)
            .collect(Collectors.toSet());
        if (!eligible.containsAll(requested.personUserIds())) {
            throw new InvalidReviewCoverageException(
                "Every selected person must be an eligible linked workspace member"
            );
        }
    }

    private boolean widens(long workspaceId, WorkspaceReviewScope current, WorkspaceReviewScope proposed) {
        for (RepositoryToMonitor monitor : monitorRepository.findByWorkspaceId(workspaceId)) {
            ReviewRepositoryTarget before = selected(current, monitor.getNameWithOwner());
            ReviewRepositoryTarget after = selected(proposed, monitor.getNameWithOwner());
            boolean beforeRepository = current.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED || before != null;
            boolean afterRepository = proposed.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED || after != null;
            if (afterRepository && !beforeRepository) return true;
            if (!afterRepository || !beforeRepository) continue;
            List<String> beforeBranches =
                current.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED || before == null
                    ? List.of()
                    : before.baseBranches();
            List<String> afterBranches =
                proposed.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED || after == null
                    ? List.of()
                    : after.baseBranches();
            if (!beforeBranches.isEmpty() && afterBranches.isEmpty()) return true;
            if (
                !beforeBranches.isEmpty() &&
                !afterBranches.isEmpty() &&
                afterBranches.stream().anyMatch(branch -> !beforeBranches.contains(branch))
            ) return true;
        }
        for (WorkspaceMembership membership : eligibleMemberships(workspaceId)) {
            Long userId = membership.getUserId();
            if (proposed.admitsPerson(userId) && !current.admitsPerson(userId)) return true;
        }
        return false;
    }

    private static @Nullable ReviewRepositoryTarget selected(WorkspaceReviewScope scope, String nameWithOwner) {
        return scope
            .repositories()
            .stream()
            .filter(repository -> repository.nameWithOwner().equals(nameWithOwner))
            .findFirst()
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean admits(
        Workspace workspace,
        @Nullable String repositoryNameWithOwner,
        @Nullable String baseBranch,
        @Nullable ReviewSubject subject
    ) {
        return readAssessment(workspace, repositoryNameWithOwner, baseBranch, subject, true).admitted();
    }

    @Transactional(readOnly = true)
    public boolean admits(
        Workspace workspace,
        @Nullable String repositoryNameWithOwner,
        @Nullable String baseBranch,
        @Nullable ReviewSubject subject,
        boolean branchRestrictionsApply
    ) {
        return readAssessment(
            workspace,
            repositoryNameWithOwner,
            baseBranch,
            subject,
            branchRestrictionsApply
        ).admitted();
    }

    @Transactional(readOnly = true)
    public CoverageAssessment assess(
        Workspace workspace,
        @Nullable String repositoryNameWithOwner,
        @Nullable String baseBranch,
        @Nullable ReviewSubject subject,
        boolean branchRestrictionsApply
    ) {
        return readAssessment(workspace, repositoryNameWithOwner, baseBranch, subject, branchRestrictionsApply);
    }

    private CoverageAssessment readAssessment(
        Workspace workspace,
        @Nullable String repositoryNameWithOwner,
        @Nullable String baseBranch,
        @Nullable ReviewSubject subject,
        boolean branchRestrictionsApply
    ) {
        WorkspaceReviewScope scope = readScope(workspace);
        ReviewSubjectStatus subjectStatus;
        if (subject == null || subject.actorId() == null) {
            subjectStatus = ReviewSubjectStatus.MISSING;
        } else if (!subject.human()) {
            subjectStatus = ReviewSubjectStatus.NON_HUMAN;
        } else if (membershipRepository.findByWorkspace_IdAndUser_Id(workspace.getId(), subject.actorId()).isEmpty()) {
            subjectStatus = ReviewSubjectStatus.UNLINKED;
        } else {
            subjectStatus = ReviewSubjectStatus.RESOLVED_LINKED_HUMAN;
        }

        ReviewRepositoryTarget selectedRepository = scope
            .repositories()
            .stream()
            .filter(repository -> repository.nameWithOwner().equals(repositoryNameWithOwner))
            .findFirst()
            .orElse(null);
        boolean repositoryMatched =
            scope.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED || selectedRepository != null;
        boolean branchMatched =
            repositoryMatched &&
            (!branchRestrictionsApply ||
                scope.repositoryMode() == ReviewRepositoryMode.ALL_MONITORED ||
                selectedRepository == null ||
                selectedRepository.baseBranches().isEmpty() ||
                (baseBranch != null && selectedRepository.baseBranches().contains(baseBranch)));
        boolean personMatched =
            subjectStatus == ReviewSubjectStatus.RESOLVED_LINKED_HUMAN &&
            subject != null &&
            scope.admitsPerson(subject.actorId());
        return new CoverageAssessment(
            scope.repositoryMode(),
            scope.personMode(),
            subjectStatus,
            repositoryMatched,
            branchMatched,
            personMatched,
            repositoryMatched && branchMatched && personMatched
        );
    }

    public record CoverageAssessment(
        ReviewRepositoryMode repositoryMode,
        ReviewPersonMode personMode,
        ReviewSubjectStatus subjectStatus,
        boolean repositoryMatched,
        boolean branchMatched,
        boolean personMatched,
        boolean admitted
    ) {}

    @Transactional
    public void replace(Workspace workspace, WorkspaceReviewScope requested) {
        long workspaceId = workspace.getId();
        validate(workspaceId, requested);
        Map<String, RepositoryToMonitor> monitorsByName = monitorRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .collect(Collectors.toMap(RepositoryToMonitor::getNameWithOwner, Function.identity()));
        Set<Long> requestedPeople = Set.copyOf(requested.personUserIds());

        repositoryTargetRepository.deleteByWorkspaceId(workspaceId);
        personTargetRepository.deleteByWorkspaceId(workspaceId);

        if (requested.repositoryMode() == ReviewRepositoryMode.SELECTED) {
            for (ReviewRepositoryTarget selection : requested.repositories()) {
                RepositoryToMonitor monitor = java.util.Objects.requireNonNull(
                    monitorsByName.get(selection.nameWithOwner()),
                    "validated repository"
                );
                repositoryTargetRepository.save(
                    new PracticeReviewRepositoryTarget(workspaceId, monitor.getId(), selection.baseBranches())
                );
            }
        }

        if (requested.personMode() == ReviewPersonMode.SELECTED) {
            personTargetRepository.saveAll(
                requestedPeople
                    .stream()
                    .map(userId -> new PracticeReviewPersonTarget(workspaceId, userId))
                    .toList()
            );
        }
        workspace.getReviewSettings().applyRollout(requested.repositoryMode(), requested.personMode(), null);
    }

    private List<WorkspaceMembership> eligibleMemberships(long workspaceId) {
        return membershipRepository
            .findAllWithUserByWorkspaceId(workspaceId)
            .stream()
            .filter(WorkspaceMembership::hasHumanUser)
            .toList();
    }
}

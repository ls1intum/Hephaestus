package de.tum.cit.aet.hephaestus.activity.overview;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyFence;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.MembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PublicActivityObjectionService {
    private final MembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final MembershipService memberVisibility;
    private final Clock clock;
    private final ActivityPeopleQueryRepository people;
    private final PublicRepositoryVisibility visibility;
    private final ConfigAuditPort audit;
    private final PersonDataCopyFence copyFence;
    private final PersonDataWriteFence writeFence;

    @Transactional(readOnly = true)
    public long hiddenPeople(long workspaceId) {
        return people.countPublicHiddenPeople(workspaceId, visibility.confirmedAfter());
    }

    /** The people an admin hid who have no membership: the only ones no other screen can show again. */
    @Transactional(readOnly = true)
    public List<UserInfoDTO> hiddenContributors(long workspaceId) {
        return people.findHiddenContributors(workspaceId).stream()
                .map(ActivityPeopleQueryRepository.ContributorRow::person)
                .toList();
    }

    @Transactional
    public void hide(long workspaceId, long userId, boolean hide) {
        copyFence.holdForCapture();
        if (!people.canClassify(workspaceId, userId)) throw new EntityNotFoundException("Contributor", userId);
        long accountId = memberVisibility.accountIdForUser(userId);
        if (!writeFence.holdForAccountWrite(accountId)) throw new EntityNotFoundException("Contributor", userId);
        var workspace = workspaces.findByIdForUpdate(workspaceId).orElseThrow();
        var member = memberships.findByWorkspace_IdAndAccount_Id(workspaceId, accountId);
        boolean before = member.map(Membership::isHidden).orElse(false);
        if (before == hide) return;
        if (member.isPresent() && member.get().getLeftAt() == null) {
            memberVisibility.updateMemberVisibility(workspaceId, accountId, hide);
            return;
        }
        var retainedMember = member.orElseGet(() -> {
            var created = memberVisibility.createMembership(workspace, accountId, Membership.WorkspaceRole.MEMBER);
            created.setLeftAt(clock.instant());
            return created;
        });
        retainedMember.setHidden(hide);
        memberships.save(retainedMember);
        audit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_VISIBILITY,
                workspace.getId(),
                workspaceId,
                new ObjectionSnapshot(before),
                new ObjectionSnapshot(hide)));
    }

    record ObjectionSnapshot(boolean contributorHidden) implements ConfigAuditSnapshot {}
}

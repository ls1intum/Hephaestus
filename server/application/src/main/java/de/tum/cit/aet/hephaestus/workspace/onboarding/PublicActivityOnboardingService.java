package de.tum.cit.aet.hephaestus.workspace.onboarding;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPublicActivity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.settings.spi.PublicActivityPolicy;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@WorkspaceAgnostic("A signed-in visitor can read and answer only their own public-workspace onboarding")
class PublicActivityOnboardingService {
    private final WorkspaceRepository workspaces;
    private final MembershipRepository onboarding;
    private final AccountPublicActivity choice;
    private final PublicActivityPolicy policy;

    @Transactional(readOnly = true)
    public PublicActivityOnboardingController.PublicActivityOnboardingDTO get(long account, String slug) {
        var workspace = requireWorkspace(slug);
        boolean seen = onboarding
                .findByWorkspace_IdAndAccount_Id(workspace.getId(), account)
                .map(Membership::isPublicActivitySeen)
                .orElse(false);
        return new PublicActivityOnboardingController.PublicActivityOnboardingDTO(seen, choice.visible(account));
    }

    @Transactional
    public PublicActivityOnboardingController.PublicActivityOnboardingDTO answer(
            long account, String slug, boolean visible) {
        var selected = requireWorkspace(slug);
        // The workspace lock serializes first answers with the existing onboarding writer.
        var workspace = workspaces.findByIdForUpdate(selected.getId()).orElseThrow();
        var row = onboarding
                .findByWorkspace_IdAndAccount_Id(workspace.getId(), account)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Join this workspace before you save its onboarding" + " choices."));
        choice.setVisible(account, visible);
        row.setPublicActivitySeen(true);
        onboarding.save(row);
        return new PublicActivityOnboardingController.PublicActivityOnboardingDTO(true, visible);
    }

    private Workspace requireWorkspace(String slug) {
        var workspace = workspaces.findPublicActivityWorkspace(slug);
        if (!policy.allowed() || workspace.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This public activity page is not available.");
        }
        return workspace.get();
    }
}

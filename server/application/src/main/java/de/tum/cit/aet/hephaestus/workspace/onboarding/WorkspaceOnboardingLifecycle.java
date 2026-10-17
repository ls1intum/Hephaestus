package de.tum.cit.aet.hephaestus.workspace.onboarding;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountErasureContributor;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
class WorkspaceOnboardingLifecycle implements AccountErasureContributor, WorkspacePurgeContributor {
    private final WorkspaceOnboardingSettingsRepository settings;
    private final MembershipRepository members;

    @Override
    public void eraseAccount(long accountId) {
        members.deleteByAccountId(accountId);
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        settings.deleteByWorkspaceId(workspaceId);
    }
}

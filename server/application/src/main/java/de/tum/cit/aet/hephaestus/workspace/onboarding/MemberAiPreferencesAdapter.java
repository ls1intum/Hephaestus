package de.tum.cit.aet.hephaestus.workspace.onboarding;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.spi.MemberAiPreferences;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account choices apply to actual developers, including unclaimed roster accounts. Explicit nobody
 * assignments have no person choice; every copied person's consent remains a capture requirement.
 */
@Service
@RequiredArgsConstructor
class MemberAiPreferencesAdapter implements MemberAiPreferences {
    private final WorkspaceOnboardingSettingsRepository settings;
    private final AccountDirectory choices;

    @Override
    @Transactional(readOnly = true)
    public Decision forDeveloper(long workspaceId, @Nullable Long developerId) {
        boolean required = settings.findByWorkspaceId(workspaceId)
                .map(WorkspaceOnboardingSettings::isAiChoiceRequired)
                .orElse(false);
        if (developerId == null) return new Decision(false, null);
        var account = choices.find(developerId).orElseThrow(() -> new EntityNotFoundException("Account", developerId));
        var choice = account.aiChoice();
        // A saved answer remains binding even when an owner turns off the setup page.
        if (choice != null) return new Decision(true, choice);
        return new Decision(required, null);
    }
}

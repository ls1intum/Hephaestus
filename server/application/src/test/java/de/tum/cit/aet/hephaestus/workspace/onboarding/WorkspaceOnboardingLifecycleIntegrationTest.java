package de.tum.cit.aet.hephaestus.workspace.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAiChoiceExport;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WorkspaceOnboardingLifecycleIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    private MembershipRepository members;

    @Autowired
    private AccountRepository choices;

    @Autowired
    private WorkspaceOnboardingSettingsRepository settings;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountAiChoiceExport exports;

    @Autowired
    private WorkspaceOnboardingLifecycle lifecycle;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void shouldExportOnlyOwnChoiceAndEraseOnlyTheRequestedAccountOrWorkspace() {
        var first =
                createWorkspace("privacy-first", "First", "first", AccountType.ORG, persistUser("privacy-owner-one"));
        var second = createWorkspace(
                "privacy-second", "Second", "second", AccountType.ORG, persistUser("privacy-owner-two"));
        long account = Objects.requireNonNull(
                accounts.save(new Account("Export subject")).getId());
        long other = Objects.requireNonNull(
                accounts.save(new Account("Other member")).getId());
        chose(account, AiChoice.NO_AI);
        chose(other, AiChoice.IN_HOUSE_ONLY);
        seen(first, account);
        seen(second, account);
        seen(first, other);
        seen(second, other);
        var policy = new WorkspaceOnboardingSettings();
        policy.setWorkspace(first);
        settings.save(policy);

        var exported = exports.choice(account);
        assertThat(exported).isNotNull();
        assertThat(exported.aiChoice()).isEqualTo("NO_AI");

        new TransactionTemplate(transactions).executeWithoutResult(status -> lifecycle.eraseAccount(account));
        assertThat(members.findById_AccountId(account)).isEmpty();
        assertThat(members.findByWorkspace_IdAndId_AccountId(first.getId(), account))
                .isEmpty();
        var otherExport = exports.choice(other);
        assertThat(otherExport).isNotNull();
        assertThat(otherExport.aiChoice()).isEqualTo("IN_HOUSE_ONLY");
        assertThat(members.findByWorkspace_IdAndId_AccountId(first.getId(), other))
                .isPresent();
        assertThat(settings.findByWorkspaceId(first.getId())).isPresent();

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> lifecycle.deleteWorkspaceData(first.getId()));
        assertThat(settings.findByWorkspaceId(first.getId())).isEmpty();
        assertThat(members.findByWorkspace_IdAndId_AccountId(first.getId(), other))
                .isEmpty();
        assertThat(members.findByWorkspace_IdAndId_AccountId(second.getId(), other))
                .isPresent();
        // Purging a workspace never touches a member's account-level answer.
        assertThat(exports.choice(other)).isNotNull();
    }

    private void chose(long accountId, AiChoice choice) {
        var row = accounts.findById(accountId).orElseThrow();
        row.setAiChoice(choice);
        row.setAiChoiceAt(Instant.now());
        choices.save(row);
    }

    private void seen(Workspace workspace, long accountId) {
        var member = new Membership();
        member.setWorkspace(workspace);
        member.setId(new Membership.Id(workspace.getId(), accountId));
        member.setId(new Membership.Id(workspace.getId(), accountId));
        member.setOnboardingSeenRevision(0L);

        members.save(member);
    }
}

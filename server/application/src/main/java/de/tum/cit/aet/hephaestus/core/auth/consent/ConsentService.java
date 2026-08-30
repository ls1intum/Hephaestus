package de.tum.cit.aet.hephaestus.core.auth.consent;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnServerRole
@WorkspaceAgnostic("Consent decisions belong to an account, not a workspace")
public class ConsentService {

    private final ConsentDecisionRepository decisionRepository;
    private final ConsentNoticeRepository noticeRepository;
    private final AccountRepository accountRepository;

    public ConsentService(
            ConsentDecisionRepository decisionRepository,
            ConsentNoticeRepository noticeRepository,
            AccountRepository accountRepository) {
        this.decisionRepository = decisionRepository;
        this.noticeRepository = noticeRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public ConsentStatusDTO status(Long accountId) {
        return currentStatus(accountId);
    }

    private ConsentStatusDTO currentStatus(Long accountId) {
        ConsentNotice notice = currentNotice();
        ConsentDecision research = latest(accountId, ConsentDecision.Purpose.RESEARCH_PARTICIPATION);
        return new ConsentStatusDTO(
                notice.getVersion(),
                notice.getNoticeText(),
                isCurrentNoticeCompleted(accountId),
                research != null && research.isGranted() && notice.getVersion().equals(research.getNoticeVersion()));
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedCurrentNotice(Long accountId) {
        return isCurrentNoticeCompleted(accountId);
    }

    private boolean isCurrentNoticeCompleted(Long accountId) {
        return decisionRepository.isCompletedForNotice(accountId, ConsentNotice.CURRENT_VERSION);
    }

    @Transactional
    public ConsentStatusDTO completeFirstLogin(Long accountId, FirstLoginConsentDTO request) {
        ConsentNotice notice = currentNotice();
        if (!notice.getVersion().equals(request.noticeVersion())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The transparency notice has changed; review it again");
        }
        if (!request.termsAccepted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terms must be accepted to use Hephaestus");
        }
        Account account = requireAccountForUpdate(accountId);
        if (!hasCurrentGrant(accountId, ConsentDecision.Purpose.TERMS_ACCEPTANCE, notice.getVersion())) {
            append(
                    account,
                    ConsentDecision.Purpose.TERMS_ACCEPTANCE,
                    true,
                    ConsentDecision.Mechanism.FIRST_LOGIN_INTERSTITIAL,
                    notice);
        }
        if (!hasCurrentGrant(accountId, ConsentDecision.Purpose.PRIVACY_NOTICE_ACKNOWLEDGEMENT, notice.getVersion())) {
            append(
                    account,
                    ConsentDecision.Purpose.PRIVACY_NOTICE_ACKNOWLEDGEMENT,
                    true,
                    ConsentDecision.Mechanism.FIRST_LOGIN_INTERSTITIAL,
                    notice);
        }
        ConsentDecision research = latest(accountId, ConsentDecision.Purpose.RESEARCH_PARTICIPATION);
        if (research == null || !notice.getVersion().equals(research.getNoticeVersion())) {
            append(
                    account,
                    ConsentDecision.Purpose.RESEARCH_PARTICIPATION,
                    request.participateInResearch(),
                    ConsentDecision.Mechanism.FIRST_LOGIN_INTERSTITIAL,
                    notice);
        }
        decisionRepository.flush();
        return currentStatus(accountId);
    }

    @Transactional
    public ConsentStatusDTO setResearchParticipation(Long accountId, ResearchConsentDTO request) {
        if (!isCurrentNoticeCompleted(accountId)) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "Complete the current transparency notice first");
        }
        ConsentNotice notice = currentNotice();
        Account account = requireAccountForUpdate(accountId);
        ConsentDecision current = latest(accountId, ConsentDecision.Purpose.RESEARCH_PARTICIPATION);
        if (current == null || current.isGranted() != request.granted()) {
            append(
                    account,
                    ConsentDecision.Purpose.RESEARCH_PARTICIPATION,
                    request.granted(),
                    ConsentDecision.Mechanism.ACCOUNT_SETTINGS,
                    notice);
        }
        decisionRepository.flush();
        return currentStatus(accountId);
    }

    @Transactional(readOnly = true)
    public boolean participatesInResearch(Long accountId) {
        ConsentDecision latest = latest(accountId, ConsentDecision.Purpose.RESEARCH_PARTICIPATION);
        return latest != null && latest.isGranted() && ConsentNotice.CURRENT_VERSION.equals(latest.getNoticeVersion());
    }

    private ConsentNotice currentNotice() {
        return noticeRepository
                .findById(ConsentNotice.CURRENT_VERSION)
                .orElseThrow(() -> new IllegalStateException("Current consent notice is not archived"));
    }

    private Account requireAccountForUpdate(Long accountId) {
        return accountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private boolean hasCurrentGrant(Long accountId, ConsentDecision.Purpose purpose, String noticeVersion) {
        ConsentDecision decision = latest(accountId, purpose);
        return decision != null && decision.isGranted() && noticeVersion.equals(decision.getNoticeVersion());
    }

    private @Nullable ConsentDecision latest(Long accountId, ConsentDecision.Purpose purpose) {
        return decisionRepository
                .findFirstByAccountIdAndPurposeOrderByOccurredAtDescIdDesc(accountId, purpose)
                .orElse(null);
    }

    private void append(
            Account account,
            ConsentDecision.Purpose purpose,
            boolean granted,
            ConsentDecision.Mechanism mechanism,
            ConsentNotice notice) {
        decisionRepository.save(
                new ConsentDecision(account, purpose, granted, mechanism, notice.getVersion(), notice.getSha256()));
    }

    public record ConsentStatusDTO(
            @NonNull String noticeVersion,
            @NonNull String noticeText,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean completed,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean participateInResearch) {}

    public record FirstLoginConsentDTO(
            @NonNull String noticeVersion,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean termsAccepted,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean participateInResearch) {
        public FirstLoginConsentDTO {
            Objects.requireNonNull(noticeVersion, "noticeVersion");
        }
    }

    public record ResearchConsentDTO(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean granted) {}
}

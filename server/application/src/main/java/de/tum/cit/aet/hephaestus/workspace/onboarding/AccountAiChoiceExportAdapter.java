package de.tum.cit.aet.hephaestus.workspace.onboarding;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAiChoiceExport;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AccountAiChoiceExportAdapter implements AccountAiChoiceExport {
    private final AccountDirectory choices;

    @Override
    @Transactional(readOnly = true)
    public @Nullable Choice choice(long accountId) {
        return choices.find(accountId)
                .filter(row -> row.aiChoice() != null)
                .map(row -> new Choice(
                        Objects.requireNonNull(row.aiChoice()).name(), Objects.requireNonNull(row.aiChoiceAt())))
                .orElse(null);
    }
}

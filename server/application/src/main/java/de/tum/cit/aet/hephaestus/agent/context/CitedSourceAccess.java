package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.spi.ResultProofAccess;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Current result visibility is owned by practices; a captured quote alone grants no access. */
@Component
@RequiredArgsConstructor
public class CitedSourceAccess {
    private final ResultProofAccess results;

    public boolean permits(long workspaceId, UUID resultId, SourceUsePurpose purpose) {
        return results.permits(workspaceId, resultId, purpose);
    }
}

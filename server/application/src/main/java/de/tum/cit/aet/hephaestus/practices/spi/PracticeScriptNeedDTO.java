package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeScriptNeedDTO(ModelSlot slot, Need need, List<DataHandlingTier> unmetTiers) {
    public PracticeScriptNeedDTO {
        unmetTiers = List.copyOf(unmetTiers);
    }
}

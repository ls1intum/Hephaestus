package de.tum.in.www1.hephaestus.gitprovider.label.dto;

import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;

@Component
public class LabelDTOConverter {

    public LabelInfoDTO convertToDTO(Label label) {
        return new LabelInfoDTO(
                label.getId(),
                label.getName(),
                label.getColor());
    }
}

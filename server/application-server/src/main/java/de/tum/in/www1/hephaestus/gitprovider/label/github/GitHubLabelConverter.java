package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import org.kohsuke.github.GHLabel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubLabelConverter implements Converter<GHLabel, Label> {

    @Override
    public Label convert(@NonNull GHLabel source) {
        return update(source, new Label());
    }

    public Label update(@NonNull GHLabel source, @NonNull Label label) {
        label.setId(source.getId());
        label.setName(source.getName());
        label.setDescription(sanitizeText(source.getDescription()));
        label.setColor(source.getColor());
        return label;
    }

    private String sanitizeText(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}

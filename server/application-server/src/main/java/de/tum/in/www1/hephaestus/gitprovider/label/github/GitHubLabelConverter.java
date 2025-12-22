package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import org.kohsuke.github.GHLabel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Converts hub4j GHLabel to domain Label entities.
 *
 * @deprecated This converter uses hub4j types and will be removed once sync services
 *             are migrated to use GraphQL. Use {@link GitHubLabelProcessor} with DTOs instead.
 */
@Deprecated(forRemoval = true)
@Component
public class GitHubLabelConverter implements Converter<GHLabel, Label> {

    @Override
    public Label convert(@NonNull GHLabel source) {
        return update(source, new Label());
    }

    public Label update(@NonNull GHLabel source, @NonNull Label label) {
        label.setId(source.getId());
        label.setName(source.getName());
        label.setDescription(PostgresStringUtils.sanitize(source.getDescription()));
        label.setColor(source.getColor());
        return label;
    }
}

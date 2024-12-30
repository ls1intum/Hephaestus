package de.tum.in.www1.hephaestus.gitprovider.common;

import java.io.IOException;
import org.kohsuke.github.GHObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public abstract class BaseGitServiceEntityConverter<S extends GHObject, T extends BaseGitServiceEntity>
    implements Converter<S, T> {

    private static final Logger logger = LoggerFactory.getLogger(BaseGitServiceEntityConverter.class);

    public abstract T update(@NonNull S source, @NonNull T target);

    protected void convertBaseFields(S source, T target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target must not be null");
        }

        // Map common fields
        target.setId(source.getId());

        try {
            target.setCreatedAt(DateUtil.convertToOffsetDateTime(source.getCreatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert createdAt field for source {}: {}", source.getId(), e.getMessage());
            target.setCreatedAt(null);
        }

        try {
            target.setUpdatedAt(DateUtil.convertToOffsetDateTime(source.getUpdatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
            target.setUpdatedAt(null);
        }
    }
}

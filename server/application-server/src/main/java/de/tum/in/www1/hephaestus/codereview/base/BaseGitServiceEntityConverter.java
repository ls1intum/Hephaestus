package de.tum.in.www1.hephaestus.codereview.base;

import org.kohsuke.github.GHObject;
import org.springframework.core.convert.converter.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public abstract class BaseGitServiceEntityConverter<S extends GHObject, T extends BaseGitServiceEntity>
        implements Converter<S, T> {

    private static final Logger logger = LoggerFactory.getLogger(BaseGitServiceEntityConverter.class);

    protected void convertBaseFields(S source, T target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target must not be null");
        }

        // Map common fields
        target.setId(source.getId());

        try {
            target.setCreatedAt(convertToOffsetDateTime(source.getCreatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert createdAt field for source {}: {}", source.getId(), e.getMessage());
            target.setCreatedAt(null);
        }

        try {
            target.setUpdatedAt(convertToOffsetDateTime(source.getUpdatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
            target.setUpdatedAt(null);
        }
    }

    private OffsetDateTime convertToOffsetDateTime(Date date) {
        return date != null ? date.toInstant().atOffset(ZoneOffset.UTC) : null;
    }
}
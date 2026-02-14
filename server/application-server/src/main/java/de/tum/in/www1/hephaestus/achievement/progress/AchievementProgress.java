package de.tum.in.www1.hephaestus.achievement.progress;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Strategy Interface for persisting achievement progress for the evaluator in the database.
 * <p>Implementations define how the achievements progress is structured.
 * @implNote Subclasses should follow the naming convention of {@code <ProgressStructureType>AchievementProgress}
 * (e.g. {@link LinearAchievementProgress}) to be included by OpenAPIs type schema generation.
 *
 */

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = LinearAchievementProgress.class, name = "LINEAR"),
    @JsonSubTypes.Type(value = BinaryAchievementProgress.class, name = "BINARY"),
})
@Schema(
    name = "AchievementProgress",
    description = "Polymorphic progress data"
)
public interface AchievementProgress {
}

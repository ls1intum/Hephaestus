package de.tum.in.www1.hephaestus.achievement.progress;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

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
    description = "Polymorphic progress data",
    oneOf = {
        LinearAchievementProgress.class,
        BinaryAchievementProgress.class
    }
)
public interface AchievementProgress {}

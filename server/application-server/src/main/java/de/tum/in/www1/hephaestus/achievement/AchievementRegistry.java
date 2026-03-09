package de.tum.in.www1.hephaestus.achievement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Registry acting as the central source of truth for all achievements defined in achievements.yml.
 * Replaces the previous AchievementDefinition enum.
 * This component parses the YAML file, resolves parent relationships into references,
 * and maintains indices for efficient querying.
 */
@Slf4j
@Service
public class AchievementRegistry {

    private static final String ACHIEVEMENTS_FILE_PATH = "achievements/achievements.yml";

    private final ObjectMapper yamlMapper;

    // We use ConcurrentHashMap to allow thread-safe reads even while replacing the data
    private Map<String, AchievementDefinition> achievementsById = new ConcurrentHashMap<>();
    private List<AchievementDefinition> allAchievements = new ArrayList<>();

    public AchievementRegistry() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new ParameterNamesModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * Reloads the achievements configuration from achievements.yml.
     * Rebuilds all internal maps and parent relationships.
     */
    public synchronized void reload() {
        try {
            Resource resource = new ClassPathResource(ACHIEVEMENTS_FILE_PATH);
            String sourcePath = "src/main/resources/" + ACHIEVEMENTS_FILE_PATH;
            File sourceFile = new File(sourcePath);

            // In development, prefer the source file over the classpath resource
            // This allows hot-reloading without requiring a recompile/resource sync
            if (sourceFile.exists()) {
                log.info("Found source file at {}, using it for hot-reload.", sourceFile.getAbsolutePath());
                resource = new FileSystemResource(sourceFile);
            } else {
                // Also try relative to the submodule if we are at project root
                File submoduleSourceFile = new File("server/application-server/" + sourcePath);
                if (submoduleSourceFile.exists()) {
                    log.info(
                        "Found source file at {}, using it for hot-reload.",
                        submoduleSourceFile.getAbsolutePath()
                    );
                    resource = new FileSystemResource(submoduleSourceFile);
                } else {
                    log.info("Loading achievements from classpath: {}", ACHIEVEMENTS_FILE_PATH);
                }
            }

            try (InputStream is = resource.getInputStream()) {
                Map<String, List<AchievementDefinition>> root = yamlMapper.readValue(is, new TypeReference<>() {});

                List<AchievementDefinition> records = root.getOrDefault("achievements", List.of());

                // Temporary map to hold the new records
                Map<String, AchievementDefinition> tempIdMap = new HashMap<>();
                for (AchievementDefinition record : records) {
                    if (tempIdMap.containsKey(record.id())) {
                        log.warn("Duplicate achievement ID found in YAML: {}. Skipping duplicate.", record.id());
                        continue;
                    }
                    tempIdMap.put(record.id(), record);
                }

                // Verify parent references
                boolean hasErrors = false;
                for (AchievementDefinition record : records) {
                    if (record.parent() != null && !record.parent().isEmpty()) {
                        if (!tempIdMap.containsKey(record.parent())) {
                            log.error("Achievement '{}' references unknown parent: '{}'", record.id(), record.parent());
                            hasErrors = true;
                        }
                    }
                }

                if (hasErrors) {
                    log.error("Achievements configuration contains errors. Please check the logs above.");
                    // We still proceed with what we have, or could decide to not update if we want "all or nothing"
                }

                // Swap maps atomically-ish (we are in synchronized block)
                this.achievementsById = new ConcurrentHashMap<>(tempIdMap);
                this.allAchievements = new ArrayList<>(records);

                log.info("Successfully loaded {} achievements into the registry.", achievementsById.size());
            }
        } catch (Exception e) {
            log.error("Failed to load achievements.yml! The registry might be empty or outdated.", e);
        }
    }

    /**
     * Look up an achievement by ID.
     *
     * @param id the achievement ID
     * @return the definition record
     * @throws IllegalArgumentException if the ID is not found
     */
    public AchievementDefinition getById(String id) {
        AchievementDefinition record = achievementsById.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown achievement ID: " + id);
        }
        return record;
    }

    /**
     * Look up an achievement by ID safely.
     *
     * @param id the achievement ID
     * @return an Optional containing the definition record if found
     */
    public Optional<AchievementDefinition> findById(String id) {
        return Optional.ofNullable(achievementsById.get(id));
    }

    /**
     * Get all achievements matching a given category.
     *
     * @param category the category to filter by
     * @return a list of achievements in the category, ordered by rarity
     */
    public List<AchievementDefinition> getByCategory(AchievementCategory category) {
        return allAchievements
            .stream()
            .filter(a -> a.category() == category)
            .sorted(Comparator.comparing(AchievementDefinition::rarity, AchievementRarity.RARITY_COMPARATOR))
            .toList();
    }

    /**
     * Get all achievements that are triggered by a particular event type.
     *
     * @param eventType the triggering event
     * @return list of achievements
     */
    public List<AchievementDefinition> getByTriggerEvent(ActivityEventType eventType) {
        return allAchievements
            .stream()
            .filter(a -> a.triggerEvents() != null && a.triggerEvents().contains(eventType))
            .toList();
    }

    /**
     * Get all registered achievement IDs.
     *
     * @return a set of all achievement IDs
     */
    public Set<String> getAchievementIds() {
        return Collections.unmodifiableSet(achievementsById.keySet());
    }

    /**
     * Get all registered achievements.
     *
     * @return an unmodifiable list of all achievements
     */
    public List<AchievementDefinition> values() {
        return Collections.unmodifiableList(allAchievements);
    }
}

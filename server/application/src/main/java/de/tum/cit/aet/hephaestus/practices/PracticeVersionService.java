package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogPracticeDocument;
import de.tum.cit.aet.hephaestus.practices.dto.HistoricalPracticeDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeVersionDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class PracticeVersionService {
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final JsonMapper mapper;
    private final PracticeDefinitionValidator validator;
    private final AccountDirectory accounts;
    private final AccountReferenceService accountReferences;

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    @Transactional(readOnly = true)
    public PracticeVersion forReview(Practice practice) {
        return practice.getCurrentVersion();
    }

    @Transactional(readOnly = true)
    public List<PracticeVersion> forReview(Long workspaceId, Collection<String> slugs) {
        return practices.findByWorkspaceIdAndSlugIn(workspaceId, slugs).stream()
                .map(Practice::getCurrentVersion)
                .toList();
    }

    public PracticeDefinition definitionOf(PracticeVersion version) {
        if (!available(version.getDefinition())) {
            throw new IllegalArgumentException("This historical definition is incomplete and cannot run a review.");
        }
        return mapper.treeToValue(version.getDefinition(), PracticeDefinition.class);
    }

    public @Nullable CatalogPracticeDocument catalogBaseOf(Practice practice) {
        JsonNode base = practice.getCatalogBase();
        if (base == null || !available(base.path("definition"))) return null;
        return mapper.treeToValue(base, CatalogPracticeDocument.class);
    }

    public boolean available(JsonNode node) {
        if (!node.isObject()
                || Arrays.stream(PracticeDefinitionField.values()).anyMatch(field -> !node.has(key(field))))
            return false;
        try {
            mapper.treeToValue(node, PracticeDefinition.class);
            return true;
        } catch (JacksonException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional
    public PracticeVersion append(Practice practice, PracticeDefinition definition, @Nullable Long createdByAccountId) {
        Practice locked = practices
                .findByIdForUpdate(practice.getId())
                .orElseThrow(() -> new EntityNotFoundException("Practice", practice.getSlug()));
        validator.validate(locked.getKind(), definition);
        int number = versions.findFirstByPracticeIdOrderByNumberDesc(locked.getId())
                .map(version -> version.getNumber() + 1)
                .orElse(1);
        PracticeVersion version = versions.saveAndFlush(
                new PracticeVersion(locked, number, mapper.valueToTree(definition), createdByAccountId));
        locked.setCurrentVersion(version);
        practice.setCurrentVersion(version);
        practices.save(locked);
        return version;
    }

    @Transactional
    public Practice create(NewPractice request) {
        var workspace = request.workspace();
        var slug = request.slug();
        var kind = request.kind();
        var definition = request.definition();
        var groupId = request.groupId();
        var position = request.position();
        var createdByAccountId = request.createdByAccountId();
        validator.validate(kind, definition);
        // The deferred current-version link permits reserving both identities before inserting
        // the practice and its first version.
        Long practiceId = (Long) Objects.requireNonNull(entityManager)
                .createNativeQuery("SELECT nextval(pg_get_serial_sequence('practice', 'id'))", Long.class)
                .getSingleResult();
        Long versionId = (Long) Objects.requireNonNull(entityManager)
                .createNativeQuery("SELECT nextval(pg_get_serial_sequence('practice_version'," + " 'id'))", Long.class)
                .getSingleResult();
        Objects.requireNonNull(entityManager)
                .createNativeQuery("""
                    INSERT INTO practice(id, workspace_id, slug, kind, group_id, position, current_version_id)
                    VALUES (:id, :workspace, :slug, :kind, :group, :position, :version)
                    """)
                .setParameter("id", practiceId)
                .setParameter("workspace", workspace.getId())
                .setParameter("slug", slug)
                .setParameter("kind", kind.value())
                .setParameter("group", groupId)
                .setParameter("position", position)
                .setParameter("version", versionId)
                .executeUpdate();
        Objects.requireNonNull(entityManager)
                .createNativeQuery("""
                    INSERT INTO practice_version(id, workspace_id, practice_id, number, definition, created_by_account_id)
                    VALUES (:id, :workspace, :practice, 1, CAST(:definition AS jsonb), :creator)
                    """)
                .setParameter("id", versionId)
                .setParameter("workspace", workspace.getId())
                .setParameter("practice", practiceId)
                .setParameter("definition", mapper.writeValueAsString(definition))
                .setParameter("creator", createdByAccountId)
                .executeUpdate();
        return practices.findByWorkspaceIdAndSlug(workspace.getId(), slug).orElseThrow();
    }

    public record NewPractice(
            Workspace workspace,
            String slug,
            ItemKind kind,
            PracticeDefinition definition,
            @Nullable Long groupId,
            int position,
            @Nullable Long createdByAccountId) {}

    @Transactional(readOnly = true)
    public PracticeVersionDTO viewCurrent(Practice practice) {
        var version = practice.getCurrentVersion();
        var previous = versions.findFirstByPracticeIdAndNumberLessThanOrderByNumberDesc(
                        practice.getId(), version.getNumber())
                .orElse(null);
        return view(version, previous, createdBy(version));
    }

    @Transactional(readOnly = true)
    public List<PracticeVersionDTO> history(Practice practice) {
        List<PracticeVersionDTO> history = new ArrayList<>();
        PracticeVersion previous = null;
        for (PracticeVersion version :
                versions.findByWorkspaceIdAndPracticeIdOrderByNumberAsc(practice.getWorkspaceId(), practice.getId())) {
            history.add(view(version, previous, createdBy(version)));
            previous = version;
        }
        return history.reversed();
    }

    public @Nullable AccountRefDTO createdBy(PracticeVersion version) {
        Long creator = version.getCreatedByAccountId();
        return creator == null
                ? null
                : accounts.find(creator)
                        .map(account -> accountReferences.reference(version.getWorkspaceId(), creator))
                        .orElse(null);
    }

    public PracticeVersionDTO view(
            PracticeVersion version, @Nullable PracticeVersion previous, @Nullable AccountRefDTO createdBy) {
        boolean complete = available(version.getDefinition());
        List<PracticeDefinitionField> changed = previous == null
                ? List.of()
                : Arrays.stream(PracticeDefinitionField.values())
                        .filter(field -> !Objects.equals(
                                previous.getDefinition().get(key(field)),
                                version.getDefinition().get(key(field))))
                        .toList();
        return new PracticeVersionDTO(
                Objects.requireNonNull(version.getId()).toString(),
                version.getNumber(),
                version.getName(),
                complete ? definitionOf(version) : null,
                complete,
                complete ? null : historical(version.getDefinition()),
                version.getReviewDigest(),
                version.getCreatedAt(),
                createdBy,
                changed,
                previous != null && !previous.getReviewDigest().equals(version.getReviewDigest()));
    }

    private HistoricalPracticeDefinitionDTO historical(JsonNode node) {
        List<PracticeDefinitionField> known = Arrays.stream(PracticeDefinitionField.values())
                .filter(field -> known(node, field))
                .toList();
        return new HistoricalPracticeDefinitionDTO(
                known,
                nullable(node, "name", String.class),
                nullable(node, "reviewOn", ReviewOn.class),
                nullable(node, "appliesWhen", AppliesWhen.class),
                nullable(node, "about", AboutRole.class),
                nullable(node, "reads", PracticeReads.class),
                nullable(node, "reviewedBy", ReviewedBy.class),
                nullable(node, "criteria", String.class),
                nullable(node, "script", String.class),
                nullable(node, "scan", ScanDefinition.class),
                nullable(node, "lineNotes", Boolean.class),
                nullable(node, "guidance", PracticeGuidance.class));
    }

    private <T> @Nullable T nullable(JsonNode node, String field, Class<T> type) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            return mapper.treeToValue(value, type);
        } catch (JacksonException | IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean known(JsonNode node, PracticeDefinitionField field) {
        JsonNode value = node.get(key(field));
        if (value == null) return false;
        if (value.isNull()) {
            return switch (field) {
                case APPLIES_WHEN, ABOUT, SCRIPT, SCAN -> true;
                default -> false;
            };
        }
        Class<?> type =
                switch (field) {
                    case NAME, CRITERIA, SCRIPT -> String.class;
                    case REVIEW_ON -> ReviewOn.class;
                    case APPLIES_WHEN -> AppliesWhen.class;
                    case ABOUT -> AboutRole.class;
                    case READS -> PracticeReads.class;
                    case REVIEWED_BY -> ReviewedBy.class;
                    case SCAN -> ScanDefinition.class;
                    case LINE_NOTES -> Boolean.class;
                    case GUIDANCE -> PracticeGuidance.class;
                };
        return nullable(node, key(field), type) != null;
    }

    public static String key(PracticeDefinitionField field) {
        return switch (field) {
            case NAME -> "name";
            case REVIEW_ON -> "reviewOn";
            case APPLIES_WHEN -> "appliesWhen";
            case ABOUT -> "about";
            case READS -> "reads";
            case REVIEWED_BY -> "reviewedBy";
            case CRITERIA -> "criteria";
            case SCRIPT -> "script";
            case SCAN -> "scan";
            case LINE_NOTES -> "lineNotes";
            case GUIDANCE -> "guidance";
        };
    }
}

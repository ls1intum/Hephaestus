package de.tum.cit.aet.hephaestus.evidence.internal;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalog;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.CaptureTimeBasis;
import de.tum.cit.aet.hephaestus.evidence.CompletenessPolicy;
import de.tum.cit.aet.hephaestus.evidence.ErasurePolicy;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.FreshnessMode;
import de.tum.cit.aet.hephaestus.evidence.FreshnessPolicy;
import de.tum.cit.aet.hephaestus.evidence.MissingnessKind;
import de.tum.cit.aet.hephaestus.evidence.PrivacyClass;
import de.tum.cit.aet.hephaestus.evidence.RetentionPolicy;
import de.tum.cit.aet.hephaestus.evidence.SourceAuthority;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseBasis;
import de.tum.cit.aet.hephaestus.evidence.SourceUseDecision;
import de.tum.cit.aet.hephaestus.evidence.SourceUseMode;
import de.tum.cit.aet.hephaestus.evidence.SourceUseOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class ClasspathArtifactSourceCatalogRegistry implements ArtifactSourceCatalogRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClasspathArtifactSourceCatalogRegistry.class);

    static final SourceContractVersion CURRENT_VERSION = new SourceContractVersion("1.0.0");
    static final String CATALOG_RESOURCE = "contracts/artifact-source/1.0.0/catalog.json";
    static final String USE_DECISIONS_RESOURCE = "contracts/artifact-source/1.0.0/source-use-decisions.json";

    private final ArtifactSourceCatalog catalog;
    private final String catalogDigest;
    private final Map<String, SourceUseDecision> useDecisions;
    private final Set<SourceKind> authorizedSources;
    private final Clock clock;

    public ClasspathArtifactSourceCatalogRegistry(JsonMapper objectMapper, Clock clock) {
        this(objectMapper, clock, "*");
    }

    @Autowired
    public ClasspathArtifactSourceCatalogRegistry(
        JsonMapper objectMapper,
        Clock clock,
        @Value("${hephaestus.evidence.authorized-source-kinds:}") String authorizedSourceKinds
    ) {
        this.clock = clock;
        byte[] catalogBytes = readBytes(CATALOG_RESOURCE);
        this.catalog = parse(read(objectMapper, catalogBytes, CATALOG_RESOURCE));
        this.catalogDigest = sha256(catalogBytes);
        this.useDecisions = parseUseDecisions(read(objectMapper, USE_DECISIONS_RESOURCE));
        validateUseDecisions(catalog, useDecisions);
        this.authorizedSources = parseAuthorizedSources(catalog, authorizedSourceKinds);
        useDecisions
            .values()
            .stream()
            .map(SourceUseDecision::expiresAt)
            .filter(java.util.Objects::nonNull)
            .min(Instant::compareTo)
            .ifPresent(expiry -> {
                long days = ChronoUnit.DAYS.between(clock.instant(), expiry);
                if (days <= 30) {
                    log.warn(
                        "Artifact-source governance approval expires in {} day(s) at {}; renew the versioned source-use decisions before that deadline",
                        days,
                        expiry
                    );
                }
            });
    }

    @Override
    public ArtifactSourceCatalog current() {
        return catalog;
    }

    @Override
    public String catalogDigest() {
        return catalogDigest;
    }

    @Override
    public ArtifactSourceContract requireSource(SourceContractVersion version, SourceKind kind) {
        requireSupported(version);
        ArtifactSourceContract contract = catalog
            .source(kind)
            .orElseThrow(() ->
                new IllegalArgumentException("Unknown source kind for contract " + version + ": " + kind)
            );
        return contract;
    }

    @Override
    public boolean isSourceUsePermitted(SourceContractVersion version, SourceKind kind) {
        ArtifactSourceContract contract = requireSource(version, kind);
        return (
            authorizedSources.contains(kind) &&
            requireUseDecision(version, contract.useDecisionId()).permitsProductUseAt(clock.instant())
        );
    }

    @Override
    public EvidenceProfile requireProfile(SourceContractVersion version, EvidenceProfileId id) {
        requireSupported(version);
        return catalog
            .profile(id)
            .orElseThrow(() ->
                new IllegalArgumentException("Unknown evidence profile for contract " + version + ": " + id)
            );
    }

    @Override
    public SourceUseDecision requireUseDecision(SourceContractVersion version, String decisionId) {
        requireSupported(version);
        SourceUseDecision decision = useDecisions.get(decisionId);
        if (decision == null) {
            throw new IllegalArgumentException(
                "Unknown source-use decision for contract " + version + ": " + decisionId
            );
        }
        return decision;
    }

    @Override
    public Optional<Instant> earliestUseDecisionExpiry() {
        return useDecisions
            .values()
            .stream()
            .map(SourceUseDecision::expiresAt)
            .filter(java.util.Objects::nonNull)
            .min(Instant::compareTo);
    }

    private void requireSupported(SourceContractVersion version) {
        if (!catalog.version().equals(version)) {
            throw new IllegalArgumentException("Unsupported source contract version: " + version);
        }
    }

    private static Set<SourceKind> parseAuthorizedSources(ArtifactSourceCatalog catalog, String configured) {
        if (configured.isBlank()) {
            return Set.of();
        }
        if ("*".equals(configured.trim())) {
            return catalog
                .sources()
                .stream()
                .map(ArtifactSourceContract::kind)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        Set<SourceKind> authorized = new HashSet<>();
        for (String value : configured.split(",")) {
            SourceKind kind = new SourceKind(value.trim());
            if (catalog.source(kind).isEmpty()) {
                throw new IllegalStateException("Unknown authorized artifact source: " + kind);
            }
            authorized.add(kind);
        }
        return Set.copyOf(authorized);
    }

    static ArtifactSourceCatalog parse(JsonNode root) {
        requireObject(root, "catalog");
        rejectUnknown(root, Set.of("version", "sources", "profiles"), "catalog");
        SourceContractVersion version = new SourceContractVersion(requiredText(root, "version", "catalog"));
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("Catalog resource has unexpected version: " + version);
        }

        List<ArtifactSourceContract> sources = new ArrayList<>();
        for (JsonNode node : requiredArray(root, "sources", "catalog")) {
            sources.add(parseSource(node));
        }
        List<EvidenceProfile> profiles = new ArrayList<>();
        for (JsonNode node : requiredArray(root, "profiles", "catalog")) {
            profiles.add(parseProfile(node));
        }
        return new ArtifactSourceCatalog(version, sources, profiles);
    }

    private static ArtifactSourceContract parseSource(JsonNode node) {
        requireObject(node, "source");
        rejectUnknown(
            node,
            Set.of(
                "kind",
                "description",
                "selectionScope",
                "artifactTypes",
                "authority",
                "captureTime",
                "freshness",
                "completeness",
                "privacyClass",
                "supportedMissingness",
                "purpose",
                "retentionPolicy",
                "erasurePolicy",
                "useDecisionId"
            ),
            "source"
        );
        SourceKind kind = new SourceKind(requiredText(node, "kind", "source"));
        JsonNode freshness = requiredObject(node, "freshness", kind.toString());
        rejectUnknown(freshness, Set.of("mode", "maxAgeSeconds"), "freshness " + kind);
        FreshnessMode freshnessMode = enumValue(
            FreshnessMode.class,
            requiredText(freshness, "mode", "freshness " + kind),
            "freshness mode"
        );
        Long maxAgeSeconds = optionalPositiveLong(freshness, "maxAgeSeconds", "freshness " + kind);

        JsonNode completeness = requiredObject(node, "completeness", kind.toString());
        rejectUnknown(
            completeness,
            Set.of("supportsComplete", "supportsPartial", "supportsEmpty"),
            "completeness " + kind
        );

        return new ArtifactSourceContract(
            kind,
            requiredText(node, "description", kind.toString()),
            requiredText(node, "selectionScope", kind.toString()),
            textSet(node, "artifactTypes", kind.toString()),
            enumValue(SourceAuthority.class, requiredText(node, "authority", kind.toString()), "authority"),
            enumValue(CaptureTimeBasis.class, requiredText(node, "captureTime", kind.toString()), "capture time"),
            new FreshnessPolicy(freshnessMode, maxAgeSeconds),
            new CompletenessPolicy(
                requiredBoolean(completeness, "supportsComplete", kind.toString()),
                requiredBoolean(completeness, "supportsPartial", kind.toString()),
                requiredBoolean(completeness, "supportsEmpty", kind.toString())
            ),
            enumValue(PrivacyClass.class, requiredText(node, "privacyClass", kind.toString()), "privacy class"),
            enumSet(MissingnessKind.class, node, "supportedMissingness", kind.toString()),
            requiredText(node, "purpose", kind.toString()),
            enumValue(
                RetentionPolicy.class,
                requiredText(node, "retentionPolicy", kind.toString()),
                "retention policy"
            ),
            enumValue(ErasurePolicy.class, requiredText(node, "erasurePolicy", kind.toString()), "erasure policy"),
            requiredText(node, "useDecisionId", kind.toString())
        );
    }

    private static EvidenceProfile parseProfile(JsonNode node) {
        requireObject(node, "profile");
        rejectUnknown(node, Set.of("id", "version", "artifactType", "allowedSources"), "profile");
        Set<SourceKind> allowedSources = new HashSet<>();
        for (String source : textSet(node, "allowedSources", "profile")) {
            allowedSources.add(new SourceKind(source));
        }
        return new EvidenceProfile(
            new EvidenceProfileId(requiredText(node, "id", "profile")),
            new SourceContractVersion(requiredText(node, "version", "profile")),
            requiredText(node, "artifactType", "profile"),
            allowedSources
        );
    }

    static Map<String, SourceUseDecision> parseUseDecisions(JsonNode root) {
        requireObject(root, "source-use decisions");
        rejectUnknown(root, Set.of("contractVersion", "decisions"), "source-use decisions");
        SourceContractVersion version = new SourceContractVersion(
            requiredText(root, "contractVersion", "source-use decisions")
        );
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("Source-use decisions have unexpected contract version: " + version);
        }
        Map<String, SourceUseDecision> decisions = new HashMap<>();
        for (JsonNode node : requiredArray(root, "decisions", "source-use decisions")) {
            requireObject(node, "source-use decision");
            rejectUnknown(
                node,
                Set.of(
                    "id",
                    "source",
                    "purpose",
                    "mode",
                    "basis",
                    "outcome",
                    "audience",
                    "modelProcessor",
                    "retentionPolicy",
                    "erasurePolicy",
                    "recordedAt",
                    "reviewer",
                    "decidedAt",
                    "expiresAt"
                ),
                "source-use decision"
            );
            String id = requiredText(node, "id", "source-use decision");
            SourceUseDecision decision = new SourceUseDecision(
                id,
                new SourceKind(requiredText(node, "source", id)),
                requiredText(node, "purpose", id),
                enumValue(SourceUseMode.class, requiredText(node, "mode", id), "source-use mode"),
                enumValue(SourceUseBasis.class, requiredText(node, "basis", id), "source-use basis"),
                enumValue(SourceUseOutcome.class, requiredText(node, "outcome", id), "source-use outcome"),
                requiredText(node, "audience", id),
                optionalText(node, "modelProcessor", id),
                enumValue(RetentionPolicy.class, requiredText(node, "retentionPolicy", id), "retention policy"),
                enumValue(ErasurePolicy.class, requiredText(node, "erasurePolicy", id), "erasure policy"),
                requiredInstant(node, "recordedAt", id),
                optionalText(node, "reviewer", id),
                optionalInstant(node, "decidedAt", id),
                optionalInstant(node, "expiresAt", id)
            );
            if (decisions.put(id, decision) != null) {
                throw new IllegalStateException("Duplicate source-use decision: " + id);
            }
        }
        return Map.copyOf(decisions);
    }

    static void validateUseDecisions(ArtifactSourceCatalog catalog, Map<String, SourceUseDecision> decisions) {
        Set<String> referencedDecisionIds = catalog
            .sources()
            .stream()
            .map(ArtifactSourceContract::useDecisionId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!decisions.keySet().equals(referencedDecisionIds)) {
            throw new IllegalStateException("Source-use decisions must match the catalog exactly");
        }
        for (ArtifactSourceContract source : catalog.sources()) {
            SourceUseDecision decision = decisions.get(source.useDecisionId());
            if (decision == null) {
                throw new IllegalStateException("Missing source-use decision: " + source.useDecisionId());
            }
            if (!decision.source().equals(source.kind())) {
                throw new IllegalStateException("Source-use decision kind does not match: " + source.useDecisionId());
            }
            if (!decision.purpose().equals(source.purpose())) {
                throw new IllegalStateException(
                    "Source-use decision purpose does not match: " + source.useDecisionId()
                );
            }
            if (
                !decision.retentionPolicy().equals(source.retentionPolicy()) ||
                !decision.erasurePolicy().equals(source.erasurePolicy())
            ) {
                throw new IllegalStateException(
                    "Source-use decision lifecycle does not match: " + source.useDecisionId()
                );
            }
        }
    }

    private static JsonNode read(JsonMapper objectMapper, String resource) {
        return read(objectMapper, readBytes(resource), resource);
    }

    private static byte[] readBytes(String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read artifact-source contract resource: " + resource, exception);
        }
    }

    private static JsonNode read(JsonMapper objectMapper, byte[] bytes, String resource) {
        try {
            return objectMapper.readTree(bytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot parse artifact-source contract resource: " + resource, exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonNode requiredObject(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        requireObject(value, context + "." + field);
        return value;
    }

    private static void requireObject(@Nullable JsonNode node, String context) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException(context + " must be an object");
        }
    }

    private static JsonNode requiredArray(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new IllegalStateException(context + "." + field + " must be a non-empty array");
        }
        return value;
    }

    private static Set<String> textSet(JsonNode node, String field, String context) {
        Set<String> values = new HashSet<>();
        for (JsonNode value : requiredArray(node, field, context)) {
            if (!value.isString() || value.asString().isBlank()) {
                throw new IllegalStateException(context + "." + field + " must contain non-blank strings");
            }
            if (!values.add(value.asString())) {
                throw new IllegalStateException(context + "." + field + " contains duplicate: " + value.asString());
            }
        }
        return values;
    }

    private static <E extends Enum<E>> Set<E> enumSet(Class<E> type, JsonNode node, String field, String context) {
        Set<E> values = new HashSet<>();
        for (String value : textSet(node, field, context)) {
            values.add(enumValue(type, value, field));
        }
        return values;
    }

    private static String requiredText(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalStateException(context + "." + field + " must be non-blank text");
        }
        return value.asString();
    }

    private static @Nullable String optionalText(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalStateException(context + "." + field + " must be null or non-blank text");
        }
        return value.asString();
    }

    private static Instant requiredInstant(JsonNode node, String field, String context) {
        String value = requiredText(node, field, context);
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(context + "." + field + " must be an ISO-8601 instant", exception);
        }
    }

    private static @Nullable Instant optionalInstant(JsonNode node, String field, String context) {
        String value = optionalText(node, field, context);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(context + "." + field + " must be an ISO-8601 instant", exception);
        }
    }

    private static boolean requiredBoolean(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException(context + "." + field + " must be boolean");
        }
        return value.asBoolean();
    }

    private static @Nullable Long optionalPositiveLong(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asLong() <= 0) {
            throw new IllegalStateException(context + "." + field + " must be a positive integer");
        }
        return value.asLong();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown " + field + ": " + value, exception);
        }
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String context) {
        node
            .properties()
            .forEach(entry -> {
                if (!allowed.contains(entry.getKey())) {
                    throw new IllegalStateException("Unknown field in " + context + ": " + entry.getKey());
                }
            });
    }
}

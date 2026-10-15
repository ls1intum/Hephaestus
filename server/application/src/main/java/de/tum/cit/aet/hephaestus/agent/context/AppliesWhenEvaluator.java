package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.AppliesWhenCheck;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.AppliesWhenClause;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates Applies when against captured evidence.
 *
 * <p>Only complete captures can prove absence. Missing, partial, malformed, or unsupported evidence is
 * {@link AppliesWhenCheck.Result#UNDECIDABLE} and keeps the practice eligible.
 */
@Component
@ConditionalOnCaptureRole
public class AppliesWhenEvaluator {

    private final JsonMapper objectMapper;

    public AppliesWhenEvaluator(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param change the pinned change, or null when capture did not prepare it
     * @return {@code null} when the practice has no Applies when conditions
     */
    public @Nullable AppliesWhenCheck evaluate(
            @Nullable AppliesWhen subject,
            JobFolderIndex manifest,
            Map<String, byte[]> staged,
            @Nullable ReviewChange change) {
        if (subject == null) {
            return null;
        }
        Map<SourceKind, SourceCapture> captures = new HashMap<>();
        manifest.sources().forEach(capture -> captures.put(capture.kind(), capture));
        List<AppliesWhenCheck.Clause> clauses = new ArrayList<>(subject.anyOf().size());
        for (AppliesWhenClause clause : subject.anyOf()) {
            SourceKind readFrom = sourceOf(clause);
            AppliesWhenCheck.Result result = find(clause, captures.get(readFrom), change, staged);
            clauses.add(new AppliesWhenCheck.Clause(aspectOf(clause), readFrom, result));
        }
        boolean absent = clauses.stream().allMatch(clause -> clause.result() == AppliesWhenCheck.Result.NOT_FOUND);
        return new AppliesWhenCheck(absent, subject.otherwise(), clauses);
    }

    private AppliesWhenCheck.Result find(
            AppliesWhenClause clause,
            @Nullable SourceCapture capture,
            @Nullable ReviewChange change,
            Map<String, byte[]> staged) {
        if (clause.hasItems() != null && fileNameOf(clause.hasItems()).isEmpty())
            return AppliesWhenCheck.Result.UNDECIDABLE;
        if (capture == null || !(capture.state() instanceof SourceCaptureState.Available available)) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        if (available.completeness() != SourceCompleteness.COMPLETE) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        if (available.content() == SourceContentState.EMPTY) {
            return AppliesWhenCheck.Result.NOT_FOUND;
        }
        return switch (aspectOf(clause)) {
            case "CHANGED_PATH" -> changedPath(clause.changedPath(), change);
            case "DIFF_CONTAINS" -> diffText(clause.diffContains(), change);
            case "HAS_ITEMS" -> evidenceItems(clause.hasItems(), capture, staged);
            default -> throw new IllegalArgumentException("Unknown applicability aspect");
        };
    }

    private AppliesWhenCheck.Result changedPath(@Nullable List<String> globs, @Nullable ReviewChange change) {
        if (globs == null || change == null) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        List<Pattern> patterns =
                globs.stream().map(AppliesWhenEvaluator::globToPattern).toList();
        boolean matched = change.changedPaths().stream()
                .anyMatch(
                        path -> patterns.stream().anyMatch(p -> p.matcher(path).matches()));
        return matched ? AppliesWhenCheck.Result.FOUND : AppliesWhenCheck.Result.NOT_FOUND;
    }

    private AppliesWhenCheck.Result diffText(@Nullable List<String> literals, @Nullable ReviewChange change) {
        if (literals == null || change == null) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        String text = change.text();
        return literals.stream().anyMatch(text::contains)
                ? AppliesWhenCheck.Result.FOUND
                : AppliesWhenCheck.Result.NOT_FOUND;
    }

    private AppliesWhenCheck.Result evidenceItems(
            @Nullable String collection, SourceCapture capture, Map<String, byte[]> staged) {
        if (collection == null) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        String fileName = fileNameOf(collection);
        if (fileName.isEmpty()) return AppliesWhenCheck.Result.UNDECIDABLE;
        CapturedSource artifact = capture.files().stream()
                .filter(candidate -> candidate.path().endsWith(fileName))
                .findFirst()
                .orElse(null);
        byte @Nullable [] bytes = artifact == null ? null : staged.get(artifact.path());
        if (bytes == null || bytes.length == 0) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(bytes);
        } catch (RuntimeException unparseable) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        String field = fieldOf(collection);
        JsonNode items = field.isEmpty() ? root : root.path(field);
        if (!items.isArray()) {
            return AppliesWhenCheck.Result.UNDECIDABLE;
        }
        return items.isEmpty() ? AppliesWhenCheck.Result.NOT_FOUND : AppliesWhenCheck.Result.FOUND;
    }

    public static SourceKind sourceOf(AppliesWhenClause clause) {
        return new SourceKind(clause.hasItems() == null ? "scm.pull-request.diff" : clause.hasItems());
    }

    private static String aspectOf(AppliesWhenClause clause) {
        return clause.changedPath() != null
                ? "CHANGED_PATH"
                : clause.diffContains() != null ? "DIFF_CONTAINS" : "HAS_ITEMS";
    }

    private static String fileNameOf(String collection) {
        return switch (collection) {
            case "scm.review-threads" -> "review_threads.json";
            case "scm.general-review-comments" -> "general_comments.json";
            default -> "";
        };
    }

    private static String fieldOf(String collection) {
        return switch (collection) {
            case "scm.review-threads" -> "threads";
            case "scm.general-review-comments" -> "comments";
            default -> "";
        };
    }

    /**
     * Translates the glob vocabulary — {@code **}, {@code *}, {@code ?}, and literals — into a regular
     * expression. Hand-rolled rather than {@code FileSystem.getPathMatcher}, which parses the subject as
     * a {@code Path}: a diff can name a path this platform's file system would reject, and the answer to
     * that must not depend on which platform the worker runs on.
     *
     * <p>{@code **}{@code /} matches zero or more leading segments, so {@code **}{@code /pom.xml} finds a
     * root {@code pom.xml} as well as a nested one. Requiring both forms is the mistake every author
     * would otherwise make once, in the direction that silently skips a practice.
     */
    static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() * 2).append('^');
        int index = 0;
        while (index < glob.length()) {
            char current = glob.charAt(index);
            if (current == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
                if (index + 2 < glob.length() && glob.charAt(index + 2) == '/') {
                    regex.append("(?:.*/)?");
                    index += 3;
                } else {
                    regex.append(".*");
                    index += 2;
                }
                continue;
            }
            switch (current) {
                case '*' -> regex.append("[^/]*");
                case '?' -> regex.append("[^/]");
                default -> {
                    if (current < 128 && Character.isLetterOrDigit(current)) {
                        regex.append(current);
                    } else {
                        regex.append(Pattern.quote(String.valueOf(current)));
                    }
                }
            }
            index++;
        }
        return Pattern.compile(regex.append('$').toString());
    }
}

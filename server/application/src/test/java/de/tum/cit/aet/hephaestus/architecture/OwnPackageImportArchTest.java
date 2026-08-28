package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "Please use import!" — a fully-qualified {@code de.tum.cit.aet.hephaestus.*} type reference in a code body
 * always has a same-project simple-name alternative (unlike a third-party FQN, it can never collide with a type
 * this codebase doesn't control), so it is always safe to import and never a legitimate disambiguation. This
 * pins the outcome of the 2026-07 hygiene sweep and stops the pattern from creeping back in.
 *
 * <p>Deliberately narrower than a general "no FQN in code" rule: third-party FQNs (e.g. two different
 * {@code Repository} types) sometimes ARE the correct disambiguation and must stay reviewable case by case, not
 * banned outright. Own-package FQNs have no such case, so this subset alone is safe to enforce mechanically.
 */
class OwnPackageImportArchTest extends HephaestusArchitectureTest {

    private static final Path MAIN_DIR = Path.of("src/main/java/de/tum/cit/aet/hephaestus");

    /**
     * Matches a {@code de.tum.cit.aet.hephaestus} type usage (an own-package class/enum/interface reference with
     * at least one intermediate lowercase segment, e.g. {@code integration.slack.domain.SlackTs}) that is not
     * immediately preceded by a letter, digit, dot, or quote — the last of which keeps it from firing on a
     * self-contained quoted literal such as {@code "de.tum.cit.aet.hephaestus.Foo"}. It does NOT attempt to
     * exclude JPQL/SpEL/AspectJ string bodies that merely contain a leading space before the FQN (see
     * {@link #stripNonCode}, which handles those instead).
     */
    private static final Pattern OWN_PACKAGE_FQN = Pattern.compile(
        "(?<![.\\w\"])de\\.tum\\.cit\\.aet\\.hephaestus(?:\\.[a-z][a-zA-Z0-9]*)+\\.[A-Z][A-Za-z0-9]*"
    );

    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(static\\s+)?[\\w.]+\\s*;\\s*$");

    @Test
    @DisplayName("the own-package FQN detector fires on real code usages and ignores imports/comments/strings/JPQL")
    void patternMatchesRealUsagesAndIgnoresLookalikes() {
        // Self-test of the guard: these must all be flagged as violations if they appeared bare in a code body.
        assertThat(matches("private de.tum.cit.aet.hephaestus.workspace.Workspace workspace;")).isTrue();
        assertThat(matches("de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent.EventType t = e.type();")).isTrue();
        assertThat(matches("@de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic(\"reason\")")).isTrue();

        // An import declaration is where the FQN belongs — the source scan below skips these lines outright,
        // but the raw pattern still "matches" them; that's fine since findViolations() filters by IMPORT_LINE.
        assertThat(IMPORT_LINE.matcher("import de.tum.cit.aet.hephaestus.workspace.Workspace;").matches()).isTrue();

        // Lookalikes that must NOT match: quoted literal, javadoc {@code}/{@link}, and a line comment.
        assertThat(matches("String s = \"de.tum.cit.aet.hephaestus.workspace.Workspace\";")).isFalse();
        // A javadoc continuation line only reads as a comment WITH its block delimiters — the scanner strips
        // whole files, so the self-test must hand it the same context.
        assertThat(
            matches(stripNonCode("/**\n * {@link de.tum.cit.aet.hephaestus.workspace.Workspace}\n */"))
        ).isFalse();
        assertThat(matches(stripNonCode("// see de.tum.cit.aet.hephaestus.workspace.Workspace for details"))).isFalse();

        // stripNonCode must blank out a JPQL text-block enum literal (own-package, legitimately FQN — JPA
        // resolves enum constants in @Query strings, not Java imports) so it never reaches the pattern at all.
        String jpql =
            "@Query(\n" +
            "    \"\"\"\n" +
            "    WHERE e.type = de.tum.cit.aet.hephaestus.activity.ActivityEventType.COMMENT_CREATED\n" +
            "    \"\"\"\n" +
            ")\n";
        assertThat(matches(stripNonCode(jpql))).isFalse();
    }

    @Test
    @DisplayName("no src/main source file uses a bare de.tum.cit.aet.hephaestus FQN in a code body")
    void mainSourcesImportOwnPackageTypesInsteadOfInliningTheFqn() {
        if (!Files.isDirectory(MAIN_DIR)) {
            throw new IllegalStateException(
                "Could not locate the main source directory at %s (cwd=%s)".formatted(
                    MAIN_DIR,
                    Path.of("").toAbsolutePath()
                )
            );
        }
        List<String> offenders;
        try (Stream<Path> paths = Files.walk(MAIN_DIR)) {
            offenders = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                .flatMap(p -> findViolations(p).stream())
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(offenders)
            .as(
                "own-package FQNs are always safe to import (no third-party collision is possible) — " +
                    "add an import and use the simple name instead"
            )
            .isEmpty();
    }

    private static List<String> findViolations(Path javaFile) {
        String content;
        try {
            content = Files.readString(javaFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String code = stripNonCode(content);
        String[] codeLines = code.split("\n", -1);
        String[] rawLines = content.split("\n", -1);
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < codeLines.length; i++) {
            if (IMPORT_LINE.matcher(rawLines[i]).matches()) {
                continue;
            }
            Matcher m = OWN_PACKAGE_FQN.matcher(codeLines[i]);
            if (m.find()) {
                offenders.add("%s:%d: %s".formatted(javaFile, i + 1, rawLines[i].strip()));
            }
        }
        return offenders;
    }

    private static boolean matches(String codeLine) {
        return OWN_PACKAGE_FQN.matcher(codeLine).find();
    }

    /**
     * Blanks out (space-replaces, preserving line breaks and column offsets) every line comment, block/javadoc
     * comment, char literal, string literal, and text block in {@code source}, leaving only executable code and
     * declarations for {@link #OWN_PACKAGE_FQN} to scan. This is what keeps the detector from tripping on a JPQL
     * {@code @Query} text block, an AspectJ pointcut string, or a javadoc {@code @see}/{@code @link} tag — all of
     * which legitimately spell out an own-package FQN as text, not as a Java type usage.
     */
    private static String stripNonCode(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int n = source.length();
        int i = 0;
        State state = State.CODE;
        while (i < n) {
            char c = source.charAt(i);
            switch (state) {
                case CODE -> {
                    if (startsWith(source, i, "//")) {
                        int j = source.indexOf('\n', i);
                        int end = j == -1 ? n : j;
                        out.append(" ".repeat(end - i));
                        i = end;
                    } else if (startsWith(source, i, "/*")) {
                        state = State.BLOCK_COMMENT;
                        out.append("  ");
                        i += 2;
                    } else if (startsWith(source, i, "\"\"\"")) {
                        state = State.TEXT_BLOCK;
                        out.append("   ");
                        i += 3;
                    } else if (c == '"') {
                        state = State.STRING;
                        out.append(' ');
                        i += 1;
                    } else if (c == '\'') {
                        state = State.CHAR;
                        out.append(' ');
                        i += 1;
                    } else {
                        out.append(c);
                        i += 1;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (startsWith(source, i, "*/")) {
                        state = State.CODE;
                        out.append("  ");
                        i += 2;
                    } else {
                        out.append(c == '\n' ? '\n' : ' ');
                        i += 1;
                    }
                }
                case TEXT_BLOCK -> {
                    if (startsWith(source, i, "\"\"\"")) {
                        state = State.CODE;
                        out.append("   ");
                        i += 3;
                    } else {
                        out.append(c == '\n' ? '\n' : ' ');
                        i += 1;
                    }
                }
                case STRING -> {
                    if (c == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                    } else if (c == '"' || c == '\n') {
                        state = State.CODE;
                        out.append(c == '\n' ? '\n' : ' ');
                        i += 1;
                    } else {
                        out.append(' ');
                        i += 1;
                    }
                }
                case CHAR -> {
                    if (c == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                    } else if (c == '\'' || c == '\n') {
                        state = State.CODE;
                        out.append(c == '\n' ? '\n' : ' ');
                        i += 1;
                    } else {
                        out.append(' ');
                        i += 1;
                    }
                }
            }
        }
        return out.toString();
    }

    private static boolean startsWith(String s, int offset, String prefix) {
        return s.regionMatches(offset, prefix, 0, prefix.length());
    }

    private enum State {
        CODE,
        BLOCK_COMMENT,
        TEXT_BLOCK,
        STRING,
        CHAR,
    }
}

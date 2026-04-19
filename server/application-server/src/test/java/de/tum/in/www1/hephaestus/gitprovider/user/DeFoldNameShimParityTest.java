package de.tum.in.www1.hephaestus.gitprovider.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards against drift between the Liquibase-managed {@code de_fold_name(text)}
 * function body (authoritative source at
 * {@code src/main/resources/db/changelog/1776600000000_changelog.xml}) and the
 * test-only re-declaration at {@code src/test/resources/sql/de_fold_name_function.sql}.
 *
 * <p>Integration tests run with {@code spring.liquibase.enabled=false}, so the
 * migration-authored function is not applied to the test schema. The shim re-creates
 * it so Hibernate's {@code function('de_fold_name', ...)} resolves. If someone changes
 * the migration (e.g. adds a new umlaut mapping) without updating the shim, the
 * integration tests pass locally but production behaviour diverges silently. This
 * test fails loudly when the two bodies go out of sync.
 */
@DisplayName("de_fold_name shim ↔ migration parity")
class DeFoldNameShimParityTest extends BaseUnitTest {

    private static final Path MIGRATION = Paths.get("src/main/resources/db/changelog/1776600000000_changelog.xml");
    private static final Path SHIM = Paths.get("src/test/resources/sql/de_fold_name_function.sql");

    private static final Pattern BODY = Pattern.compile(
        "CREATE\\s+OR\\s+REPLACE\\s+FUNCTION\\s+de_fold_name\\s*\\(.*?\\$\\$(.*?)\\$\\$",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    @Test
    @DisplayName("shim body matches migration body after whitespace normalisation")
    void shimBodyMatchesMigrationBody() throws IOException {
        String migrationBody = extractFunctionBody(Files.readString(MIGRATION, StandardCharsets.UTF_8));
        String shimBody = extractFunctionBody(Files.readString(SHIM, StandardCharsets.UTF_8));

        assertThat(normalise(shimBody))
            .as(
                "src/test/resources/sql/de_fold_name_function.sql must re-declare the exact " +
                    "body of the de_fold_name function defined in 1776600000000_changelog.xml. " +
                    "Update the shim whenever the migration body changes."
            )
            .isEqualTo(normalise(migrationBody));
    }

    private static String extractFunctionBody(String source) {
        Matcher m = BODY.matcher(source);
        assertThat(m.find()).as("de_fold_name CREATE OR REPLACE block not found in %s", source).isTrue();
        return m.group(1);
    }

    private static String normalise(String body) {
        return body.replaceAll("\\s+", " ").trim();
    }
}

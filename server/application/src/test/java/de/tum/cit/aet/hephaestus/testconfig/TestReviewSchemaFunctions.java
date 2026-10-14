package de.tum.cit.aet.hephaestus.testconfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Hibernate tests use the production function definitions; migration tests use the whole changelog. */
final class TestReviewSchemaFunctions {
    private static final Set<String> NAMES = Set.of(
            "practice_review_digest", "review_model_digest", "feedback_content_digest", "feedback_package_digest");

    private TestReviewSchemaFunctions() {}

    static void install(Connection connection, String... names) throws SQLException {
        final String changelog;
        try (var input = Objects.requireNonNull(
                TestReviewSchemaFunctions.class.getResourceAsStream("/db/changelog/1791486276211_changelog.xml"))) {
            changelog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read the production review functions", exception);
        }
        try (var statement = connection.createStatement()) {
            for (String name : names) {
                if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown production review function");
                var matcher = Pattern.compile("(?m)^CREATE FUNCTION " + Pattern.quote(name) + "\\([\\s\\S]*?\\$\\$;")
                        .matcher(changelog);
                if (!matcher.find()) throw new IllegalStateException("Missing production function " + name);
                String ddl = matcher.group();
                if (matcher.find()) throw new IllegalStateException("Duplicate production function " + name);
                statement.execute(ddl.replaceFirst("CREATE FUNCTION", "CREATE OR REPLACE FUNCTION"));
            }
        }
    }
}

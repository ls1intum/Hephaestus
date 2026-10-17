package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class E2eIdentitySeedDatabaseTest {
    @Test
    void shouldPreserveDisabledErasedAndForeignIdentitiesWhenTheE2eSeederRuns() throws Exception {
        var database = NativePracticeFixture.database("prod");
        var builder = new ProcessBuilder("node", "--test", "../../scripts/e2e-identity-seed-database.ts")
                .redirectErrorStream(true);
        builder.environment().put("E2E_TEST_DATABASE_URL", database.jdbcUrl().substring("jdbc:".length()));
        builder.environment().put("E2E_TEST_DATABASE_USER", database.username());
        builder.environment().put("E2E_TEST_DATABASE_PASSWORD", database.password());
        var process = builder.start();
        try {
            assertThat(process.waitFor(60, TimeUnit.SECONDS))
                    .as("identity seed proof completed")
                    .isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}

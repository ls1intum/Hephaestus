package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.SecretDiffScanner.SecretHit;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SecretDiffScanner — deterministic secret pre-pass over a unified diff")
class SecretDiffScannerTest extends BaseUnitTest {

    private final SecretDiffScanner scanner = new SecretDiffScanner();

    // Synthetic fixtures assembled from split literals so secret-scanning push protection does not flag
    // test data: no contiguous provider-pattern token exists in source, yet the runtime value is intact.
    private static final String OPENAI_KEY = "sk-" + "weather-abc123def456";
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7REALKEY1";
    private static final String GITLAB_PAT = "glpat-" + "3hQ7kZ9mW2pX5tR8vL1q";
    private static final String PG_URL = "postgres://admin:" + "s3cr3tPwd@db.internal:5432/app";
    private static final String HIGH_ENTROPY = "Hs7Kp2" + "Lm9Qz4Xv1Rt8Bw";

    private static String diff(String path, String... addedLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("diff --git a/").append(path).append(" b/").append(path).append("\n");
        sb.append("--- a/").append(path).append("\n");
        sb.append("+++ b/").append(path).append("\n");
        sb.append("@@ -1,1 +1,").append(addedLines.length).append(" @@\n");
        for (String l : addedLines) {
            sb.append("+").append(l).append("\n");
        }
        return sb.toString();
    }

    @Nested
    class CatchesRealSecrets {

        @Test
        @DisplayName("the live-grade regression: a hardcoded apiKey literal on a '+' line")
        void liveGradeOpenAiKey() {
            List<SecretHit> hits = scanner.scan(
                diff("FocusBoard/WeatherView.swift", "    let apiKey = \"" + OPENAI_KEY + "\"")
            );
            assertThat(hits).isNotEmpty();
            SecretHit hit = hits.get(0);
            assertThat(hit.path()).isEqualTo("FocusBoard/WeatherView.swift");
            assertThat(hit.newLine()).isEqualTo(1);
            assertThat(hit.isCritical()).isTrue();
            assertThat(hit.addedLine()).contains(OPENAI_KEY);
        }

        @Test
        void awsAccessKey() {
            List<SecretHit> hits = scanner.scan(diff("src/config.py", "AWS_KEY = \"" + AWS_KEY + "\""));
            assertThat(hits).anyMatch(h -> h.ruleId().equals("aws-access-key"));
        }

        @Test
        void gitlabPat() {
            List<SecretHit> hits = scanner.scan(diff("ci.env", "TOKEN=" + GITLAB_PAT));
            assertThat(hits).anyMatch(h -> h.ruleId().equals("gitlab-pat"));
        }

        @Test
        void privateKeyBlock() {
            List<SecretHit> hits = scanner.scan(diff("id_rsa", "-----BEGIN RSA PRIVATE KEY-----"));
            assertThat(hits).anyMatch(h -> h.ruleId().equals("private-key") && h.isCritical());
        }

        @Test
        void connectionStringWithCredentials() {
            List<SecretHit> hits = scanner.scan(diff("app/db.ts", "const url = \"" + PG_URL + "\";"));
            assertThat(hits).anyMatch(h -> h.ruleId().equals("connection-string") && h.isCritical());
        }

        @Test
        void genericHighEntropyAssignment() {
            List<SecretHit> hits = scanner.scan(diff("server.js", "const secret = \"" + HIGH_ENTROPY + "\";"));
            assertThat(hits).anyMatch(h -> h.ruleId().equals("generic-entropy") && !h.isCritical());
        }
    }

    @Nested
    class SuppressesFalsePositives {

        @Test
        @DisplayName("env-var read is not a hardcode")
        void envReference() {
            List<SecretHit> hits = scanner.scan(
                diff("WeatherView.swift", "let apiKey = ProcessInfo.processInfo.environment[\"API_KEY\"]")
            );
            assertThat(hits).isEmpty();
        }

        @Test
        void processEnvReference() {
            List<SecretHit> hits = scanner.scan(diff("config.ts", "const token = process.env.SECRET_TOKEN;"));
            assertThat(hits).isEmpty();
        }

        @Test
        void placeholderValue() {
            List<SecretHit> hits = scanner.scan(diff("README.md", "api_key = \"YOUR_API_KEY_HERE\""));
            assertThat(hits).isEmpty();
        }

        @Test
        void lowEntropyAssignmentNotFlagged() {
            List<SecretHit> hits = scanner.scan(diff("config.yml", "password: \"password1234\""));
            // low entropy / dictionary-ish → not a generic-entropy hit
            assertThat(hits).noneMatch(h -> h.ruleId().equals("generic-entropy"));
        }

        @Test
        void commentedLineSkipped() {
            List<SecretHit> hits = scanner.scan(diff("notes.js", "// old key was " + OPENAI_KEY));
            assertThat(hits).isEmpty();
        }

        @Test
        void removedLinesIgnored() {
            String d =
                "diff --git a/app.py b/app.py\n--- a/app.py\n+++ b/app.py\n@@ -1,2 +1,1 @@\n" +
                "-API_KEY = \"" +
                OPENAI_KEY +
                "\"\n const x = 1\n";
            assertThat(scanner.scan(d)).isEmpty();
        }

        @Test
        void nullAndBlankDiff() {
            assertThat(scanner.scan(null)).isEmpty();
            assertThat(scanner.scan("   ")).isEmpty();
        }
    }

    @Nested
    class LineNumbering {

        @Test
        @DisplayName("new-side line number tracks context + added lines from the hunk header")
        void tracksLineNumbers() {
            String d =
                "diff --git a/a.py b/a.py\n--- a/a.py\n+++ b/a.py\n@@ -10,2 +10,3 @@\n" +
                " context_line\n" +
                "+filler = 1\n" +
                "+key = \"" +
                OPENAI_KEY +
                "\"\n";
            List<SecretHit> hits = scanner.scan(d);
            assertThat(hits).isNotEmpty();
            // hunk starts at new line 10: context=10, filler=11, key=12
            assertThat(hits.get(0).newLine()).isEqualTo(12);
        }
    }

    @Nested
    class PathSignal {

        @Test
        void testPathIsLowSignal() {
            assertThat(scanner.isLowSignalPath("src/test/fixtures/keys.txt")).isTrue();
            assertThat(scanner.isLowSignalPath("examples/demo.env")).isTrue();
            assertThat(scanner.isLowSignalPath("src/main/Weather.swift")).isFalse();
        }
    }
}

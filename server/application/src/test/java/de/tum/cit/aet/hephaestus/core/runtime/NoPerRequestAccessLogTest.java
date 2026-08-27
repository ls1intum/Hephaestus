package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * {@code application-prod.yml} states, and {@code docs/admin/dsms/} publishes as a GDPR posture, that
 * NO layer of the deployment writes a per-request IP/URL record. That is a claim about every layer at
 * once, only one of which is Java — so it is asserted here over the shipped configuration of each
 * rather than trusted per file.
 *
 * <p>The URL is the personal datum, not the address: neither nginx image loads {@code real_ip}, so the
 * peer they would log is the proxy's container address. But an SPA deep link names a person
 * ({@code /w/<slug>/user/<username>}), so one combined-format line per request is a per-request
 * personal-data record regardless.
 *
 * <p>nginx inherits {@code access_log} downwards, so the guard is structural: {@code off} must sit in
 * the {@code server} block, where every location inherits it. A per-location {@code access_log off}
 * covers only the locations that remember to repeat it, and this file is edited by adding locations.
 */
class NoPerRequestAccessLogTest extends BaseUnitTest {

    private static final Path WEBAPP_NGINX_CONF = Path.of("..", "..", "webapp", "docker", "nginx.conf");
    private static final Path PROXY_COMPOSE = Path.of("..", "..", "docker", "compose.proxy.yaml");
    private static final Path PROD_PROFILE = Path.of("src", "main", "resources", "application-prod.yml");

    @Test
    void tomcatAccessLogStaysDisabledInTheProductionProfile() throws Exception {
        assertThat(propertyIn(PROD_PROFILE, "server.tomcat.accesslog.enabled"))
                .as("application-prod.yml claims no layer writes a per-request record; Tomcat is one of them")
                .isEqualTo("false");
    }

    @Test
    void webappNginxDisablesTheAccessLogForTheWholeServerBlock() throws Exception {
        List<String> serverLevel = directivesAtServerLevel(Files.readString(WEBAPP_NGINX_CONF, StandardCharsets.UTF_8));

        assertThat(serverLevel)
                .as("webapp/docker/nginx.conf must carry `access_log off;` in the server block: "
                        + "nginx:stable-alpine turns the access log ON at http level, so anything the server "
                        + "block does not override logs the request URL — and SPA deep links name contributors")
                .contains("access_log off");
    }

    @Test
    void maintenancePageNginxDisablesTheAccessLogForTheWholeServerBlock() throws Exception {
        String conf = propertyIn(PROXY_COMPOSE, "configs.nginx-default-config.content");

        assertThat(conf)
                .as("docker/compose.proxy.yaml no longer inlines an nginx server config under that key")
                .isNotNull();
        assertThat(directivesAtServerLevel(conf))
                .as("the maintenance page answers on a catch-all Host rule whenever the webapp router is "
                        + "down, so the URL it would log is the deep link the contributor was reaching for")
                .contains("access_log off");
    }

    /**
     * A location that sets its own {@code access_log} to a destination would opt that path back in
     * underneath the server-level {@code off}, which is the failure the server-level directive exists
     * to prevent — and it would not show up in the two tests above.
     */
    @Test
    void noNginxLocationReEnablesTheAccessLog() throws Exception {
        List<String> reEnabling = new ArrayList<>();
        for (String conf : List.of(
                Files.readString(WEBAPP_NGINX_CONF, StandardCharsets.UTF_8),
                propertyIn(PROXY_COMPOSE, "configs.nginx-default-config.content"))) {
            conf.lines()
                    .map(NoPerRequestAccessLogTest::directive)
                    .filter(line -> line.startsWith("access_log") && !line.equals("access_log off"))
                    .forEach(reEnabling::add);
        }

        assertThat(reEnabling)
                .as("every access_log directive in a shipped nginx config must be `off`")
                .isEmpty();
    }

    /**
     * Directives written directly inside the single {@code server { … }} block — brace depth 1, so a
     * {@code location} nested inside it does not count.
     */
    private static List<String> directivesAtServerLevel(String conf) {
        List<String> directives = new ArrayList<>();
        int depth = 0;
        for (String raw : conf.lines().toList()) {
            String line = directive(raw);
            if (depth == 1 && !line.isEmpty() && !line.endsWith("{") && !line.startsWith("}")) {
                directives.add(line);
            }
            depth += count(raw, '{') - count(raw, '}');
        }
        return directives;
    }

    /** Strips the trailing {@code ;}, comments and indentation so directives compare by content. */
    private static String directive(String line) {
        String stripped = line.strip();
        int comment = stripped.indexOf('#');
        if (comment >= 0) {
            stripped = stripped.substring(0, comment).strip();
        }
        return stripped.endsWith(";")
                ? stripped.substring(0, stripped.length() - 1).strip()
                : stripped;
    }

    private static int count(String line, char c) {
        return (int) line.chars().filter(ch -> ch == c).count();
    }

    private static @Nullable String propertyIn(Path yaml, String key) throws IOException {
        return new YamlPropertySourceLoader()
                .load(yaml.toString(), new FileSystemResource(yaml)).stream()
                        .map(PropertySource.class::cast)
                        .filter(source -> source.containsProperty(key))
                        .findFirst()
                        .map(source -> String.valueOf(source.getProperty(key)))
                        .orElse(null);
    }
}
